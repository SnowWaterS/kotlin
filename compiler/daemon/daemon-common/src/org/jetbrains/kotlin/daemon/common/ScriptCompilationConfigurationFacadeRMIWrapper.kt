/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.daemon.common

import kotlinx.coroutines.runBlocking
import java.io.Serializable
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptConfigurationRefinementContext
import kotlin.script.experimental.util.PropertiesCollection

class ScriptCompilationConfigurationFacadeRMIWrapper(val clientSide: ScriptCompilationConfigurationFacadeAsync) :
    ScriptCompilationConfigurationFacade, Serializable {

    override fun refineConfiguration(
        refiningKey: PropertiesCollection.Key<*>,
        context: ScriptConfigurationRefinementContext
    ): ResultWithDiagnostics<ScriptCompilationConfiguration> =
        runBlocking { clientSide.refineConfiguration(refiningKey, context) }

}

fun ScriptCompilationConfigurationFacadeAsync.toRMI() = ScriptCompilationConfigurationFacadeRMIWrapper(this)
fun CompileService.CallResult<ScriptCompilationConfigurationFacadeAsync>.toRMI() = when (this) {
    is CompileService.CallResult.Good -> CompileService.CallResult.Good(this.result.toRMI())
    is CompileService.CallResult.Dying -> this
    is CompileService.CallResult.Error -> this
    is CompileService.CallResult.Ok -> this
}

