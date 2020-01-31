/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.jsr223

import org.jetbrains.kotlin.cli.common.repl.KotlinJsr223JvmScriptEngineFactoryBase
import org.jetbrains.kotlin.cli.common.repl.ScriptArgsWithTypes
import java.io.File
import javax.script.Bindings
import javax.script.ScriptContext
import javax.script.ScriptEngine
import kotlin.script.experimental.api.KotlinType
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.host.createCompilationConfigurationFromTemplate
import kotlin.script.experimental.host.createEvaluationConfigurationFromTemplate
import kotlin.script.experimental.jvm.JvmScriptCompilationConfigurationBuilder
import kotlin.script.experimental.jvm.defaultJvmScriptingHostConfiguration
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvm.updateClasspath
import kotlin.script.experimental.jvm.util.scriptCompilationClasspathFromContext

@Suppress("unused") // used in javax.script.ScriptEngineFactory META-INF file
class KotlinJsr223StandardScriptEngineFactory4Idea : KotlinJsr223JvmScriptEngineFactoryBase() {

    private val scriptingHostConfiguration = defaultJvmScriptingHostConfiguration
    private val compilationConfiguration =
        createCompilationConfigurationFromTemplate(KotlinType(KotlinJsr223Script4Idea::class), scriptingHostConfiguration)
    private val evaluationConfiguration =
        createEvaluationConfigurationFromTemplate(KotlinType(KotlinJsr223Script4Idea::class), scriptingHostConfiguration)
    private var lastClassLoader: ClassLoader? = null
    private var lastClassPath: List<File>? = null

    @Synchronized
    protected fun JvmScriptCompilationConfigurationBuilder.dependenciesFromCurrentContext() {
        val currentClassLoader = Thread.currentThread().contextClassLoader
        val classPath = if (lastClassLoader == null || lastClassLoader != currentClassLoader) {
            scriptCompilationClasspathFromContext(
                classLoader = currentClassLoader,
                wholeClasspath = true,
                unpackJarCollections = true
            ).also {
                lastClassLoader = currentClassLoader
                lastClassPath = it
            }
        } else lastClassPath!!
        updateClasspath(classPath)
    }

    override fun getScriptEngine(): ScriptEngine =
        KotlinJsr223JvmScriptEngine4Idea(
            this,
            ScriptCompilationConfiguration(compilationConfiguration) {
                jvm {
                    dependenciesFromCurrentContext()
                }
            },
            evaluationConfiguration
        ) { ScriptArgsWithTypes(arrayOf(it.getBindings(ScriptContext.ENGINE_SCOPE).orEmpty()), arrayOf(Bindings::class)) }
}

