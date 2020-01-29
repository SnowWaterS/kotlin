/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.daemon.client

import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.daemon.common.LoopbackNetworkInterface
import org.jetbrains.kotlin.daemon.common.SOCKET_ANY_FREE_PORT
import org.jetbrains.kotlin.daemon.common.ScriptCompilationConfigurationFacade
import java.rmi.server.UnicastRemoteObject
import kotlin.script.experimental.api.*
import kotlin.script.experimental.util.PropertiesCollection

class ScriptCompilationConfigurationFacadeServer(
    val refineImpl: ScriptCompilationConfigurationRefine = DirectScriptCompilationConfigurationRefine(),
    port: Int = SOCKET_ANY_FREE_PORT
) :
    ScriptCompilationConfigurationFacade,
    UnicastRemoteObject(port, LoopbackNetworkInterface.clientLoopbackSocketFactory, LoopbackNetworkInterface.serverLoopbackSocketFactory) {

    override fun refineConfiguration(
        refiningKey: PropertiesCollection.Key<*>,
        context: ScriptConfigurationRefinementContext
    ): ResultWithDiagnostics<ScriptCompilationConfiguration> = runBlocking {
        refineImpl(refiningKey, context)
    }
}
