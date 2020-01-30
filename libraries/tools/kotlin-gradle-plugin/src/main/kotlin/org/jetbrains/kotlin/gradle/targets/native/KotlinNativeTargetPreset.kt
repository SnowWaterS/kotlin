/*
 * Copyright 2010-2018 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

// Old package for compatibility
@file:Suppress("PackageDirectoryMismatch")

package org.jetbrains.kotlin.gradle.plugin.mpp

import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.jetbrains.kotlin.compilerRunner.konanHome
import org.jetbrains.kotlin.compilerRunner.konanVersion
import org.jetbrains.kotlin.gradle.dsl.NativeDistributionType.REGULAR
import org.jetbrains.kotlin.gradle.dsl.kotlinExtension
import org.jetbrains.kotlin.gradle.plugin.*
import org.jetbrains.kotlin.gradle.plugin.PropertiesProvider
import org.jetbrains.kotlin.gradle.targets.native.DisabledNativeTargetsReporter
import org.jetbrains.kotlin.gradle.targets.native.internal.PlatformLibrariesGenerator
import org.jetbrains.kotlin.gradle.utils.NativeCompilerDownloader
import org.jetbrains.kotlin.gradle.utils.SingleWarningPerBuild
import org.jetbrains.kotlin.konan.CompilerVersion
import org.jetbrains.kotlin.konan.target.HostManager
import org.jetbrains.kotlin.konan.target.KonanTarget
import java.io.File

abstract class AbstractKotlinNativeTargetPreset<T : KotlinNativeTarget>(
    private val name: String,
    val project: Project,
    val konanTarget: KonanTarget,
    protected val kotlinPluginVersion: String
) : KotlinTargetPreset<T> {

    init {
        // This is required to obtain Kotlin/Native home in CLion plugin:
        setupNativeHomePrivateProperty()
    }

    override fun getName(): String = name

    private fun setupNativeHomePrivateProperty() = with(project) {
        if (!hasProperty(KOTLIN_NATIVE_HOME_PRIVATE_PROPERTY))
            extensions.extraProperties.set(KOTLIN_NATIVE_HOME_PRIVATE_PROPERTY, konanHome)
    }

    private val isKonanHomeOverridden: Boolean
        get() = PropertiesProvider(project).nativeHome != null

    private fun setupNativeCompiler() = with(project) {
        if (!isKonanHomeOverridden) {
            NativeCompilerDownloader(this).downloadIfNeeded()
            logger.info("Kotlin/Native distribution: $konanHome")
        } else {
            logger.info("User-provided Kotlin/Native distribution: $konanHome")
        }

        val distributionType = PropertiesProvider(project).nativeDistributionType
        if (konanVersion.isAtLeast(1, 4, 0) &&
            (distributionType == null || distributionType == REGULAR)
        ) {
            PlatformLibrariesGenerator(project, konanTarget).generatePlatformLibsIfNeeded()
        }
    }

    private fun nativeLibrariesList(directory: String) = with(project) {
        file("$konanHome/klib/$directory")
            .listFiles { file -> file.isDirectory }
            ?.sortedBy { dir -> dir.name.toLowerCase() }
    }

    // We declare default K/N dependencies (default and platform libraries) as files to avoid searching them in remote repos (see KT-28128).
    private fun defaultLibs(stdlibOnly: Boolean = false): List<File> {
        var filesList = nativeLibrariesList("common")
        if (stdlibOnly) {
            filesList = filesList?.filter { dir -> dir.name == "stdlib" }
        }
        return filesList.orEmpty()
    }

    private fun platformLibs(target: KonanTarget): List<File> =
        nativeLibrariesList("platform/${target.name}").orEmpty()

    private fun Collection<File>.asDependencies(): List<Dependency> = with(project) {
        map { dir -> dependencies.create(files(dir)) }
    }

    protected abstract fun createTargetConfigurator(): KotlinTargetConfigurator<T>

    protected abstract fun instantiateTarget(name: String): T

    override fun createTarget(name: String): T {
        setupNativeCompiler()

        val result = instantiateTarget(name).apply {
            targetName = name
            disambiguationClassifier = name
            preset = this@AbstractKotlinNativeTargetPreset

            val compilationFactory = KotlinNativeCompilationFactory(this)
            compilations = project.container(compilationFactory.itemClass, compilationFactory)
        }

        createTargetConfigurator().configureTarget(result)

        addDependenciesOnLibrariesFromDistribution(result)

        if (!konanTarget.enabledOnCurrentHost) {
            with(HostManager()) {
                val supportedHosts = enabledByHost.filterValues { konanTarget in it }.keys
                DisabledNativeTargetsReporter.reportDisabledTarget(project, result, supportedHosts)
            }
        }

        return result
    }

    // Allow IDE to resolve the libraries provided by the compiler by adding them into dependencies.
    private fun addDependenciesOnLibrariesFromDistribution(result: T) {
        result.compilations.all { compilation ->
            val target = compilation.konanTarget
            project.whenEvaluated {
                // First, put common libs:
                defaultLibs(!compilation.enableEndorsedLibs).asDependencies().forEach {
                    project.dependencies.add(compilation.compileDependencyConfigurationName, it)
                }
                // Then, platform-specific libs:
                platformLibs(target).asDependencies().forEach {
                    project.dependencies.add(compilation.compileDependencyConfigurationName, it)
                }
            }
        }

        // Add dependencies to stdlib-native for intermediate single-backend source-sets (like 'allNative')
        project.whenEvaluated {
            val compilationsBySourceSets = CompilationSourceSetUtil.compilationsBySourceSets(this)

            fun KotlinSourceSet.isIntermediateNativeSourceSet(): Boolean {
                val compilations = compilationsBySourceSets[this] ?: return false

                if (compilations.all { it.defaultSourceSet == this })
                    return false

                return compilations.all { it.target.platformType == KotlinPlatformType.native }
            }

            val stdlib = defaultLibs(stdlibOnly = true).asDependencies().singleOrNull() ?: run {
                warnAboutMissingNativeStdlib(project)
                return@whenEvaluated
            }

            project.kotlinExtension.sourceSets
                .filter { it.isIntermediateNativeSourceSet() }
                .forEach {
                    project.dependencies.add(it.implementationMetadataConfigurationName, stdlib)
                }
        }
    }

    companion object {
        private const val KOTLIN_NATIVE_HOME_PRIVATE_PROPERTY = "konanHome"

        private fun warnAboutMissingNativeStdlib(project: Project) {
            if (!project.hasProperty("kotlin.native.nostdlib")) {
                SingleWarningPerBuild.show(
                    project,
                    buildString {
                        append(NO_NATIVE_STDLIB_WARNING)
                        if (PropertiesProvider(project).nativeHome != null)
                            append(NO_NATIVE_STDLIB_PROPERTY_WARNING)
                    }
                )
            }
        }

        internal const val NO_NATIVE_STDLIB_WARNING =
            "The Kotlin/Native distribution used in this build does not provide the standard library. "

        internal const val NO_NATIVE_STDLIB_PROPERTY_WARNING =
            "Make sure that the '${PropertiesProvider.KOTLIN_NATIVE_HOME}' property points to a valid Kotlin/Native distribution."
    }

}

open class KotlinNativeTargetPreset(name: String, project: Project, konanTarget: KonanTarget, kotlinPluginVersion: String) :
    AbstractKotlinNativeTargetPreset<KotlinNativeTarget>(name, project, konanTarget, kotlinPluginVersion) {

    override fun createTargetConfigurator(): KotlinTargetConfigurator<KotlinNativeTarget> =
        KotlinNativeTargetConfigurator(kotlinPluginVersion)

    override fun instantiateTarget(name: String): KotlinNativeTarget {
        return project.objects.newInstance(KotlinNativeTarget::class.java, project, konanTarget)
    }
}

open class KotlinNativeTargetWithHostTestsPreset(name: String, project: Project, konanTarget: KonanTarget, kotlinPluginVersion: String) :
    AbstractKotlinNativeTargetPreset<KotlinNativeTargetWithHostTests>(name, project, konanTarget, kotlinPluginVersion) {

    override fun createTargetConfigurator(): KotlinNativeTargetWithHostTestsConfigurator =
        KotlinNativeTargetWithHostTestsConfigurator(kotlinPluginVersion)

    override fun instantiateTarget(name: String): KotlinNativeTargetWithHostTests =
        project.objects.newInstance(KotlinNativeTargetWithHostTests::class.java, project, konanTarget)
}

open class KotlinNativeTargetWithSimulatorTestsPreset(name: String, project: Project, konanTarget: KonanTarget, kotlinPluginVersion: String) :
    AbstractKotlinNativeTargetPreset<KotlinNativeTargetWithSimulatorTests>(name, project, konanTarget, kotlinPluginVersion) {

    override fun createTargetConfigurator(): KotlinNativeTargetWithSimulatorTestsConfigurator =
        KotlinNativeTargetWithSimulatorTestsConfigurator(kotlinPluginVersion)

    override fun instantiateTarget(name: String): KotlinNativeTargetWithSimulatorTests =
        project.objects.newInstance(KotlinNativeTargetWithSimulatorTests::class.java, project, konanTarget)
}

internal val KonanTarget.isCurrentHost: Boolean
    get() = this == HostManager.host

internal val KonanTarget.enabledOnCurrentHost
    get() = HostManager().isEnabled(this)

internal val AbstractKotlinNativeCompilation.isMainCompilation: Boolean
    get() = name == KotlinCompilation.MAIN_COMPILATION_NAME

// KonanVersion doesn't provide an API to compare versions,
// so we have to transform it to KotlinVersion first.
// Note: this check doesn't take into account the meta version (release, eap, dev).
internal fun CompilerVersion.isAtLeast(major: Int, minor: Int, patch: Int): Boolean =
    KotlinVersion(this.major, this.minor, this.maintenance).isAtLeast(major, minor, patch)