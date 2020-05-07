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
import com.beust.jcommander.Parameters
import com.haulmont.cuba.cli.green
import com.haulmont.cuba.cli.plugin.sdk.dto.Component

@Parameters(commandDescription = "Install artifacts in SDK")
class InstallCommand : BaseInstallCommand() {

    @Parameter(
        names = ["--c", "--components"],
        description = "List of components via ',' in <name>:<version> or in full coordinates format <group>:<name>:<version>. Example: cuba-7.2.1,addon-dashboard:3.2.1",
        hidden = true
    )
    private var nameVersions: String? = null

    override fun run() {
        checkRepositories(repositoryNames)?.let { repositories ->

            val components = nameVersions?.let { parseComponents(it) } ?: askComponentsWithDependencies()
            val componentsToResolve = mutableListOf<Component>()
            components.forEach {
                if (force(it) || !componentManager.isAlreadyInstalled(it)) {
                    val component = searchInMetadata(it)
                    if (force(it) || component == null) {
                        if (!componentsToResolve.contains(it)) {
                            componentsToResolve.add(it)
                        }
                    } else{
                        printWriter.println(messages["install.alreadyResolverComponent"].format(component))
                    }
                }
            }

            if (componentsToResolve.isNotEmpty()) {
                resolve(componentsToResolve)
                upload(componentsToResolve, repositories)
                printWriter.println(messages["install.finished"].green())
            } else {
                printWriter.println(messages["install.alreadyInstalled"].green())
            }
        }
    }

    override fun createSearchContext(): Component? = null
}