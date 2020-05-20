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

package com.haulmont.cuba.cli.plugin.sdk.commands.artifacts

import com.beust.jcommander.Parameter
import com.haulmont.cli.core.green
import com.haulmont.cli.core.red
import com.haulmont.cuba.cli.plugin.sdk.dto.Component

abstract class BaseInstallCommand : BaseComponentCommand() {

    @Parameter(
        names = ["--r", "--repository"],
        description = "Repository",
        hidden = true,
        variableArity = true
    )
    internal var repositoryNames: List<String>? = null

    override fun run() {
        checkRepositories(repositoryNames)?.let { repositories ->
            createSearchContext()?.let {
                printWriter.println(messages["install.start"].format(it))
                if (force(it) || !componentManager.isAlreadyInstalled(it)) {
                    var component = searchInMetadata(it)
                    val componentsToResolve = mutableListOf<Component>()
                    if (force(it) || component == null) {
                        component = search(it)?.also {
                            componentsToResolve.addAll(componentWithDependents(it))
                            resolve(componentsToResolve)
                        }
                    }
                    component?.let { upload(componentsToResolve, repositories) }
                        ?: printWriter.println(messages["resolve.notFound"].format(it).red())
                    printWriter.println(messages["install.finished"].green())
                } else {
                    printWriter.println(messages["install.alreadyInstalled"].green())
                }
            }
        }
    }
}