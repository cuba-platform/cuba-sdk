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
import com.haulmont.cuba.cli.plugin.sdk.dto.RepositoryTarget
import com.haulmont.cuba.cli.plugin.sdk.dto.RepositoryType
import com.haulmont.cuba.cli.plugin.sdk.services.RepositoryManager
import com.haulmont.cuba.cli.plugin.sdk.utils.doubleUnderline
import org.kodein.di.generic.instance
import java.io.PrintWriter

@Parameters(commandDescription = "Add source repository for SDK")
open class ListRepositoryCommand : AbstractCommand() {

    internal val messages by localMessages()
    internal val repositoryManager: RepositoryManager by sdkKodein.instance()
    internal val printWriter: PrintWriter by sdkKodein.instance()

    override fun run() {
        for (target in getTargets()) {
            printWriter.println(messages["repository.$target"].doubleUnderline())
            for (repository in repositoryManager.getRepositories(target)) {
                printWriter.println("Name: ${repository.name}")
                if (RepositoryType.LOCAL != repository.type) {
                    printWriter.println("URL: ${repository.url}")
                } else {
                    printWriter.println("Path: ${repository.url}")
                }
                printWriter.println("Type: ${repository.type}")
                if (repository.authentication != null) {
                    printWriter.println("Login: ${repository.authentication.login}")
                    printWriter.println("Password: ${repository.authentication.password}")
                }
                if (repository.repositoryName.isNotBlank()) {
                    printWriter.println("Repository name: ${repository.repositoryName}")
                }
                printWriter.println()
            }
            printWriter.println()
        }
    }

    internal open fun getTargets(): Collection<RepositoryTarget> {
        return RepositoryTarget.values().toList()
    }
}