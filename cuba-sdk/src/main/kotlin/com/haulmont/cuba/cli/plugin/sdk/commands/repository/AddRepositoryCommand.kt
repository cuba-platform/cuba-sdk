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
import com.haulmont.cli.core.prompting.Answers
import com.haulmont.cli.core.prompting.Prompts
import com.haulmont.cli.core.prompting.QuestionsList
import com.haulmont.cli.core.prompting.ValidationException
import com.haulmont.cuba.cli.plugin.sdk.commands.AbstractSdkCommand
import com.haulmont.cuba.cli.plugin.sdk.di.sdkKodein
import com.haulmont.cuba.cli.plugin.sdk.dto.Authentication
import com.haulmont.cuba.cli.plugin.sdk.dto.Repository
import com.haulmont.cuba.cli.plugin.sdk.dto.RepositoryTarget
import com.haulmont.cuba.cli.plugin.sdk.dto.RepositoryType
import com.haulmont.cuba.cli.plugin.sdk.services.RepositoryManager
import org.kodein.di.generic.instance
import java.nio.file.Paths

@Parameters(commandDescription = "Add repository for SDK")
open class AddRepositoryCommand : AbstractSdkCommand() {

    @Parameter(
        description = "Repository name",
        hidden = true
    )
    var repositoryName: String? = null

    internal val repositoryManager: RepositoryManager by sdkKodein.instance<RepositoryManager>()
    internal var target: RepositoryTarget? = null

    override fun run() {
        Prompts.create(kodein) { askRepositorySettings() }
            .let(Prompts::ask)
            .let(this::addRepository)
    }

    private fun addRepository(answers: Answers) {
        val target = target ?: RepositoryTarget.getTarget(answers["target"] as String)
        val name = repositoryName ?: answers["name"] as String
        val isLocal = answers["isLocal"] as Boolean
        val url = if (isLocal) "${answers["path"]}" else answers["url"] as String
        val authRequired = answers["auth"] as Boolean?
        val authentication = if (authRequired == true)
            Authentication(
                answers["login"] as String, answers["password"] as String
            ) else null
        val type = if (isLocal) RepositoryType.LOCAL else (answers["type"] as String?)?.let {
            getRepositoryType(it)
        }
        val repositoryName = answers["searchName"] as String?
        val repository = Repository(
            name = name,
            type = type ?: RepositoryType.NEXUS3,
            url = url,
            authentication = authentication,
            repositoryName = repositoryName ?: ""
        )
        repositoryManager.addRepository(repository, target)
        printWriter.println(messages["repository.created"].green())
    }

    private fun QuestionsList.askRepositorySettings() {
        if (target == null) {
            textOptions("target", messages["repository.target"], listOf("source", "target"))
        }
        if (repositoryName == null) {
            question("name", messages["repository.name"]) {
                validate {
                    validateRepositoryName(target ?: RepositoryTarget.getTarget(answers["target"] as String), value)
                }
            }
        } else {
            if (target != null) {
                validateRepositoryName(target!!, repositoryName!!)
            }
        }
        confirmation("isLocal", messages["repository.isLocal"]) {
            default(false)
        }
        question("url", messages["repository.url"]) {
            askIf { !isLocal(it) }
            validate { checkIsNotBlank()
                checkRegex("^(https?)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]", messages["repository.url.invalid"])}
        }
        question("path", messages["repository.path"]) {
            default(Paths.get(System.getProperty("user.home")).resolve(".m2").resolve("repository").toString())
            askIf { isLocal(it) }
            validate { checkIsNotBlank() }
        }
        confirmation("auth", messages["repository.authRequired"]) {
            default(false)
            askIf { !isLocal(it) }
        }
        question("login", messages["repository.login"]) {
            askIf { it["auth"] != null && it["auth"] as Boolean }
            validate { checkIsNotBlank() }
        }
        question("password", messages["repository.password"]) {
            askIf { it["auth"] != null && it["auth"] as Boolean }
            validate { checkIsNotBlank() }
        }
        question("type", messages["repository.type"]) {
            validate {
                if (!listOf("nexus2", "nexus3").contains(value.toLowerCase())){
                    fail(messages["repository.typeValidation"])
                }
            }
            askIf { isSearchRepository(it) && !isLocal(it) }
        }
        question("searchName", messages["repository.searchRepositoryName"]) {
            askIf { isSearchRepository(it) }
        }
    }

    private fun validateRepositoryName(target: RepositoryTarget, repositoryName: String) {
        if (repositoryName.isEmpty()) {
            fail(messages["validation.notEmpty"])
        }
        if (repositoryManager.getRepository(repositoryName, target) != null) {
            throw ValidationException("Repository with name ${repositoryName} already exist")
        }
    }

    private fun isSearchRepository(it: Answers) = it["target"] == "search"

    private fun getRepositoryType(type: String): RepositoryType = when (type) {
        "nexus2" -> RepositoryType.NEXUS2
        "nexus3" -> RepositoryType.NEXUS3
        else -> throw IllegalStateException("Unsupported repository type ${type}")
    }

    private fun isLocal(it: Answers) = (it["isLocal"] as Boolean)
}