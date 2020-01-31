/*
 * Copyright 2010-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.jsr223

import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocation
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.common.repl.*
import org.jetbrains.kotlin.daemon.client.*
import org.jetbrains.kotlin.daemon.common.*
import org.jetbrains.kotlin.utils.KotlinPaths
import org.jetbrains.kotlin.utils.PathUtil
import java.util.concurrent.locks.ReentrantReadWriteLock
import javax.script.ScriptContext
import javax.script.ScriptEngineFactory
import javax.script.ScriptException
import kotlin.script.experimental.api.*
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.host.withDefaultsFrom
import kotlin.script.experimental.jsr223.defs.getScriptContext
import kotlin.script.experimental.jsr223.defs.jsr223
import kotlin.script.experimental.jvm.defaultJvmScriptingHostConfiguration

// TODO: need to manage resources here, i.e. call replCompiler.dispose when engine is collected

class KotlinJsr223JvmScriptEngine4Idea(
    factory: ScriptEngineFactory,
    baseCompilationConfiguration: ScriptCompilationConfiguration,
    baseEvaluationConfiguration: ScriptEvaluationConfiguration,
    val getScriptArgs: (context: ScriptContext) -> ScriptArgsWithTypes?
) : KotlinJsr223JvmScriptEngineBase(factory) {

    @Volatile
    private var lastScriptContext: ScriptContext? = null

    val jsr223HostConfiguration = ScriptingHostConfiguration(defaultJvmScriptingHostConfiguration) {
        jsr223 {
            getScriptContext { lastScriptContext ?: getContext() }
        }
    }

    val compilationConfiguration by lazy {
        ScriptCompilationConfiguration(baseCompilationConfiguration) {
            hostConfiguration.update { it.withDefaultsFrom(jsr223HostConfiguration) }
            repl {
                // Snippet classes should be named uniquely, to avoid classloading clashes in the "eval in eval" scenario
                // TODO: consider applying the logic for any REPL, alternatively - develop other naming scheme to avoid clashes
                makeSnippetIdentifier { configuration, snippetId ->
                    val scriptContext: ScriptContext? = configuration[ScriptCompilationConfiguration.jsr223.getScriptContext]?.invoke()
                    val engineState = scriptContext?.let {
                        it.getBindings(ScriptContext.ENGINE_SCOPE)?.get(KOTLIN_SCRIPT_STATE_BINDINGS_KEY)
                    }
                    if (engineState == null) makeDefaultSnippetIdentifier(snippetId)
                    else "ScriptingHost${System.identityHashCode(engineState).toString(16)}_${makeDefaultSnippetIdentifier(snippetId)}"
                }
            }
        }
    }

    val evaluationConfiguration by lazy {
        ScriptEvaluationConfiguration(baseEvaluationConfiguration) {
            hostConfiguration.update { it.withDefaultsFrom(jsr223HostConfiguration) }
        }
    }

    private val daemon by lazy {
        val classPath = PathUtil.kotlinPathsForIdeaPlugin.classPath(KotlinPaths.ClassPaths.CompilerWithScripting)
        assert(classPath.all { it.exists() })
        val compilerId = CompilerId.makeCompilerId(classPath)
        val daemonOptions = configureDaemonOptions()
        val daemonJVMOptions = DaemonJVMOptions()

        val daemonReportMessages = arrayListOf<DaemonReportMessage>()

        KotlinCompilerClient.connectToCompileService(
            compilerId, daemonJVMOptions, daemonOptions,
            DaemonReportingTargets(null, daemonReportMessages),
            autostart = true, checkId = true
        ) ?: throw ScriptException(
            "Unable to connect to repl server:" + daemonReportMessages.joinToString("\n  ", prefix = "\n  ") {
                "${it.category.name} ${it.message}"
            }
        )
    }

    private val messageCollector = MyMessageCollector()

    override val replCompiler: ReplCompiler by lazy {
        KotlinRemoteReplCompilerClient(
            daemon,
            makeAutodeletingFlagFile("idea-jsr223-repl-session"),
            CompileService.TargetPlatform.JVM,
            emptyArray(),
            messageCollector,
            compilationConfiguration,
            jsr223HostConfiguration,
            ScriptCompilationConfigurationFacadeServer()
        )
    }

    override fun overrideScriptArgs(context: ScriptContext): ScriptArgsWithTypes? = getScriptArgs(getContext())

    private val localEvaluator by lazy {
        GenericReplCompilingEvaluatorBase(replCompiler, JvmReplEvaluator(evaluationConfiguration))
    }

    override val replEvaluator: ReplFullEvaluator get() = localEvaluator

    override fun createState(lock: ReentrantReadWriteLock): IReplStageState<*> = replEvaluator.createState(lock)

    private class MyMessageCollector : MessageCollector {
        private var hasErrors: Boolean = false

        override fun report(severity: CompilerMessageSeverity, message: String, location: CompilerMessageLocation?) {
            System.err.println(message) // TODO: proper location printing
            if (!hasErrors) {
                hasErrors = severity == CompilerMessageSeverity.EXCEPTION || severity == CompilerMessageSeverity.ERROR
            }
        }

        override fun clear() {}

        override fun hasErrors(): Boolean = hasErrors
    }
}
