/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.jsr223

import javax.script.Bindings
import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.jsr223.defs.KotlinJsr223DefaultScriptCompilationConfiguration
import kotlin.script.experimental.jsr223.defs.KotlinJsr223DefaultScriptEvaluationConfiguration
import kotlin.script.templates.standard.ScriptTemplateWithBindings

@Suppress("unused")
@KotlinScript(
    compilationConfiguration = KotlinJsr223DefaultScriptCompilationConfiguration::class,
    evaluationConfiguration = KotlinJsr223DefaultScriptEvaluationConfiguration::class
)
abstract class KotlinJsr223Script4Idea(val jsr223Bindings: Bindings) : ScriptTemplateWithBindings(jsr223Bindings) {

    val test42 = 42
}
