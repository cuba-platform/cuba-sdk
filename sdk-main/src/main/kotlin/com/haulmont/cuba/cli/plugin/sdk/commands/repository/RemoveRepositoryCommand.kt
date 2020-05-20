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
import com.haulmont.cli.core.green
import com.haulmont.cli.core.prompting.Prompts
import com.haulmont.cli.core.red
import com.haulmont.cuba.cli.plugin.sdk.commands.AbstractSdkCommand
import com.haulmont.cuba.cli.plugin.sdk.di.sdkKodein
import com.haulmont.cuba.cli.plugin.sdk.dto.RepositoryTarget
import com.haulmont.cuba.cli.plugin.sdk.services.RepositoryManager
import org.kodein.di.generic.instance

@Parameters(commandDescription = "Remove repository from SDK")
open class RemoveRepositoryCommand : AbstractSdkCommand() {

    internal val repositoryManager: RepositoryManager by sdkKodein.instance<RepositoryManager>()
    internal var target: RepositoryTarget? = null

    @Parameter(description = "Repository name")
    private var name: String? = null

    override fun run() {
        if (target == null) {
            val targetAnswers = Prompts.create {
                textOptions("target", messages["repository.target"], listOf("source", "target", "search"))
            }.ask()
            target = RepositoryTarget.getTarget(targetAnswers["target"] as String)
        }
        if (name == null) {
            val repositories = repositoryManager.getRepositories(target!!)
                .map { it.name }.sorted()
            val nameAnswers = Prompts.create {
                textOptions("name", messages["repository.name"], repositories)
            }.ask()
            name = nameAnswers["name"] as String
        }
        removeRepository(target!!, name!!)
    }

    private fun removeRepository(target: RepositoryTarget, name: String) {
        if (repositoryManager.getRepository(name,target)!=null) {
            repositoryManager.removeRepository(name, target)
            printWriter.println(messages["repository.removed"].green())
        } else{
            printWriter.println(messages["repository.doesNotExists"].red())
        }
    }
}