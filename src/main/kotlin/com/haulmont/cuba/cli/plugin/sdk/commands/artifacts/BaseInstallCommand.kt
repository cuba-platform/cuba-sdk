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
import com.haulmont.cuba.cli.green
import com.haulmont.cuba.cli.plugin.sdk.dto.Repository
import com.haulmont.cuba.cli.plugin.sdk.dto.RepositoryTarget
import com.haulmont.cuba.cli.red

abstract class BaseInstallCommand : BaseComponentCommand() {

    @Parameter(
        names = ["--repo"],
        description = "Repository",
        hidden = true
    )
    private var repositoryName: String? = null

    override fun run() {
        var repository: Repository? = null
        if (repositoryName != null) {
            repository = repositoryManager.getRepository(repositoryName!!, RepositoryTarget.TARGET)
            if (repository == null) {
                printWriter.println(messages["repository.unknown"].format(repositoryName).red())
                return
            }
        }
        createSearchContext()?.let {
            if (force || !componentManager.isAlreadyInstalled(it)) {
                var component = searchInMetadata(it)
                if (force || component == null) {
                    component = search(it)?.also {
                        resolve(it)
                    }
                }
                component?.let { upload(it, repository) }
                printWriter.println()
                printWriter.println(messages["install.finished"].green())
            } else {
                printWriter.println(messages["install.alreadyInstalled"].green())
            }
        }
    }
}