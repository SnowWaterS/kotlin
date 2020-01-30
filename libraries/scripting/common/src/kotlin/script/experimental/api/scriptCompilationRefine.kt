/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package kotlin.script.experimental.api

import kotlinx.coroutines.runBlocking
import kotlin.script.experimental.util.PropertiesCollection

interface ScriptCompilationConfigurationRefine {
    suspend operator fun invoke(
        refiningKey: PropertiesCollection.Key<*>,
        context: ScriptConfigurationRefinementContext
    ): ResultWithDiagnostics<ScriptCompilationConfiguration>
}

class DirectScriptCompilationConfigurationRefine : ScriptCompilationConfigurationRefine {

    override suspend operator fun invoke(
        refiningKey: PropertiesCollection.Key<*>,
        context: ScriptConfigurationRefinementContext
    ): ResultWithDiagnostics<ScriptCompilationConfiguration> = when (refiningKey) {
        ScriptCompilationConfiguration.refineConfigurationBeforeParsing ->
            context.compilationConfiguration
                .simpleRefineImpl(ScriptCompilationConfiguration.refineConfigurationBeforeParsing) { config, refineData ->
                    refineData.handler.invoke(ScriptConfigurationRefinementContext(context.script, config, context.collectedData))
                }
        ScriptCompilationConfiguration.refineConfigurationOnAnnotations -> {
            val foundAnnotationNames = context.collectedData?.get(ScriptCollectedData.foundAnnotations)
                ?.mapTo(HashSet()) { it.annotationClass.java.name }
            if (foundAnnotationNames.isNullOrEmpty()) context.compilationConfiguration.asSuccess()
            else {
                val thisResult: ResultWithDiagnostics<ScriptCompilationConfiguration> = context.compilationConfiguration.asSuccess()
                context.compilationConfiguration[ScriptCompilationConfiguration.refineConfigurationOnAnnotations]
                    ?.fold(thisResult) { config, (annotations, handler) ->
                        config.onSuccess {
                            // checking that the collected data contains expected annotations
                            if (annotations.none { foundAnnotationNames.contains(it.typeName) }) it.asSuccess()
                            else handler.invoke(
                                ScriptConfigurationRefinementContext(context.script, it, context.collectedData)
                            )
                        }
                    } ?: thisResult
            }
        }
        ScriptCompilationConfiguration.refineConfigurationBeforeCompiling ->
            context.compilationConfiguration
                .simpleRefineImpl(ScriptCompilationConfiguration.refineConfigurationBeforeCompiling) { config, refineData ->
                    refineData.handler.invoke(ScriptConfigurationRefinementContext(context.script, config, context.collectedData))
                }
        else -> ResultWithDiagnostics
            .Failure("Unknown refining key $refiningKey".asErrorDiagnostics())
    }
}

@Suppress("unused") // left for API compatibility
@Deprecated("Use ScriptCompilationConfigurationRefine implementations directly")
fun ScriptCompilationConfiguration.refineBeforeParsing(
    script: SourceCode,
    collectedData: ScriptCollectedData? = null,
    refine: ScriptCompilationConfigurationRefine = DirectScriptCompilationConfigurationRefine()
): ResultWithDiagnostics<ScriptCompilationConfiguration> =
    runBlocking {
        refine(
            ScriptCompilationConfiguration.refineConfigurationBeforeParsing,
            ScriptConfigurationRefinementContext(script, this@refineBeforeParsing, collectedData)
        )
    }

@Suppress("unused") // left for API compatibility
@Deprecated("Use ScriptCompilationConfigurationRefine implementations directly")
fun ScriptCompilationConfiguration.refineOnAnnotations(
    script: SourceCode,
    collectedData: ScriptCollectedData,
    refine: ScriptCompilationConfigurationRefine = DirectScriptCompilationConfigurationRefine()
): ResultWithDiagnostics<ScriptCompilationConfiguration> =
    runBlocking {
        refine(
            ScriptCompilationConfiguration.refineConfigurationOnAnnotations,
            ScriptConfigurationRefinementContext(script, this@refineOnAnnotations, collectedData)
        )
    }

@Suppress("unused") // left for API compatibility
@Deprecated("Use ScriptCompilationConfigurationRefine implementations directly")
fun ScriptCompilationConfiguration.refineBeforeCompiling(
    script: SourceCode,
    collectedData: ScriptCollectedData? = null,
    refine: ScriptCompilationConfigurationRefine = DirectScriptCompilationConfigurationRefine()
): ResultWithDiagnostics<ScriptCompilationConfiguration> =
    runBlocking {
        refine(
            ScriptCompilationConfiguration.refineConfigurationBeforeCompiling,
            ScriptConfigurationRefinementContext(script, this@refineBeforeCompiling, collectedData)
        )
    }

internal inline fun <Configuration: PropertiesCollection, RefineData> Configuration.simpleRefineImpl(
    key: PropertiesCollection.Key<List<RefineData>>,
    refineFn: (Configuration, RefineData) -> ResultWithDiagnostics<Configuration>
): ResultWithDiagnostics<Configuration> = (
        this[key]
            ?.fold(this) { config, refineData ->
                refineFn(config, refineData).valueOr { return it }
            } ?: this
        ).asSuccess()