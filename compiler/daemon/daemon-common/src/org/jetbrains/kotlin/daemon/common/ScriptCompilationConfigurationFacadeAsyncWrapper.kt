/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.daemon.common

import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptConfigurationRefinementContext
import kotlin.script.experimental.util.PropertiesCollection

class ScriptCompilationConfigurationFacadeAsyncWrapper(val rmiScriptCompilationConfigurationFacade: ScriptCompilationConfigurationFacade) :
    ScriptCompilationConfigurationFacadeAsync {

    override suspend fun refineConfiguration(
        refiningKey: PropertiesCollection.Key<*>,
        context: ScriptConfigurationRefinementContext
    ): ResultWithDiagnostics<ScriptCompilationConfiguration> =
        rmiScriptCompilationConfigurationFacade.refineConfiguration(refiningKey, context)

}

fun ScriptCompilationConfigurationFacade.toClient() = ScriptCompilationConfigurationFacadeAsyncWrapper(this)
fun CompileService.CallResult<ScriptCompilationConfigurationFacade>.toClient() = when (this) {
    is CompileService.CallResult.Good -> CompileService.CallResult.Good(this.result.toClient())
    is CompileService.CallResult.Dying -> this
    is CompileService.CallResult.Error -> this
    is CompileService.CallResult.Ok -> this
}
