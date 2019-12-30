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

import com.beust.jcommander.Parameters
import com.haulmont.cuba.cli.commands.AbstractCommand
import com.haulmont.cuba.cli.cubaplugin.di.sdkKodein
import com.haulmont.cuba.cli.localMessages
import com.haulmont.cuba.cli.plugin.sdk.dto.Authentication
import com.haulmont.cuba.cli.plugin.sdk.dto.Repository
import com.haulmont.cuba.cli.plugin.sdk.dto.RepositoryTarget
import com.haulmont.cuba.cli.plugin.sdk.dto.RepositoryType
import com.haulmont.cuba.cli.plugin.sdk.services.RepositoryManager
import com.haulmont.cuba.cli.prompting.Answers
import com.haulmont.cuba.cli.prompting.Prompts
import com.haulmont.cuba.cli.prompting.QuestionsList
import org.kodein.di.generic.instance
import java.io.PrintWriter
import java.nio.file.Paths

@Parameters(commandDescription = "Add repository for SDK")
open class AddRepositoryCommand : AbstractCommand() {

    internal val messages by localMessages()
    internal val printWriter: PrintWriter by sdkKodein.instance()
    internal val repositoryManager: RepositoryManager by sdkKodein.instance()
    internal var target: RepositoryTarget? = null

    override fun run() {
        Prompts.create(kodein) { askRepositorySettings() }
            .let(Prompts::ask)
            .let(this::addRepository)
    }

    private fun addRepository(answers: Answers) {
        val target = target ?: RepositoryTarget.getTarget(answers["target"] as String)
        val name = answers["name"] as String
        val isLocal = answers["isLocal"] as Boolean
        val url = answers["url"] as String
        val authRequired = answers["auth"] as Boolean?
        val authentication = if (authRequired == true)
            Authentication(
                answers["login"] as String, answers["password"] as String
            ) else null
        val type = (answers["type"] as String?)?.let {
            if (isLocal) RepositoryType.LOCAL else getRepositoryType(it)
        }
        val repositoryName = answers["searchName"] as String?
        val repository = Repository(
            name = name,
            type = type ?: RepositoryType.NEXUS2,
            url = url,
            authentication = authentication,
            repositoryName = repositoryName ?: ""
        )
        repositoryManager.addRepository(repository, target)
        printWriter.println(messages["repository.created"])
    }

    private fun QuestionsList.askRepositorySettings() {
        textOptions("target", messages["repository.target"], listOf("source", "sdk", "search")) {
            askIf { target == null }
        }
        question("name", messages["repository.name"]) {
            validate { repositoryManager.getRepository(value, RepositoryTarget.getTarget(answers["target"] as String)) }
        }
        confirmation("isLocal", messages["repository.isLocal"]) {
            default(false)
        }
        question("url", messages["repository.url"]) {
            askIf { !isLocal(it) }
        }
        question("url", messages["repository.path"]) {
            default(Paths.get(System.getProperty("user.home")).resolve(".m2").toString())
            askIf { isLocal(it) }
        }
        confirmation("auth", messages["repository.authRequired"]) {
            default(false)
            askIf { !isLocal(it) }
        }
        question("login", messages["repository.login"]) {
            askIf { it["auth"] as Boolean }
        }
        question("password", messages["repository.password"]) {
            askIf { it["auth"] as Boolean }
        }
        question("type", messages["repository.type"]) {
            validate {
                listOf("bintray", "nexus2", "nexus3").contains(value.toLowerCase())
            }
            askIf { isSearchRepository(it) && !isLocal(it) }
        }
        question("searchName", messages["repository.searchRepositoryName"]) {
            askIf { isSearchRepository(it) }
        }
    }

    private fun isSearchRepository(it: Answers) = it["target"] == "search"

    private fun getRepositoryType(type: String): RepositoryType = when (type) {
        "bintray" -> RepositoryType.BINTRAY
        "nexus2" -> RepositoryType.NEXUS2
        "nexus3" -> RepositoryType.NEXUS3
        else -> throw IllegalStateException("Unsupported repository type ${type}")
    }

    private fun isLocal(it: Answers) = (it["isLocal"] as Boolean)
}