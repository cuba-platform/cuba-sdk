/*
 * Copyright (c) 2008-2019 Haulmont.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.haulmont.cuba.cli.plugin.sdk.commands.repository

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import com.haulmont.cuba.cli.commands.AbstractCommand
import com.haulmont.cuba.cli.cubaplugin.di.sdkKodein
import com.haulmont.cuba.cli.green
import com.haulmont.cuba.cli.localMessages
import com.haulmont.cuba.cli.plugin.sdk.dto.RepositoryTarget
import com.haulmont.cuba.cli.plugin.sdk.services.RepositoryManager
import com.haulmont.cuba.cli.prompting.Answers
import com.haulmont.cuba.cli.prompting.Prompts
import com.haulmont.cuba.cli.prompting.QuestionsList
import org.kodein.di.generic.instance
import java.io.PrintWriter

@Parameters(commandDescription = "Remove repository from SDK")
open class RemoveRepositoryCommand : AbstractCommand() {

    internal val messages by localMessages()
    internal val repositoryManager: RepositoryManager by sdkKodein.instance()
    internal val printWriter: PrintWriter by sdkKodein.instance()
    internal var target: RepositoryTarget? = null

    @Parameter(description = "Repository name")
    private var name: String? = null

    override fun run() {
        if (name == null) {
            printWriter.println(messages["repository.nameRequired"])
            return
        }
        Prompts.create(kodein) { askRepositorySettings() }
            .let(Prompts::ask)
            .let(this::removeRepository)
    }

    private fun removeRepository(answers: Answers) {
        val target = target ?: RepositoryTarget.getTarget(answers["target"] as String)
        name?.let { repositoryManager.removeRepository(it, target) }
        printWriter.println(messages["repository.removed"].green())
    }

    private fun QuestionsList.askRepositorySettings() {
        textOptions("target", messages["repository.target"], listOf("source", "sdk", "search")) {
            askIf { target == null }
        }
    }
}