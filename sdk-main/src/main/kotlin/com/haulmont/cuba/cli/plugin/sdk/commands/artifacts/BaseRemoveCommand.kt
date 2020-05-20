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
import com.haulmont.cli.core.prompting.Prompts
import com.haulmont.cli.core.red
import com.haulmont.cuba.cli.plugin.sdk.commands.repository.StartCommand
import com.haulmont.cuba.cli.plugin.sdk.di.sdkKodein
import com.haulmont.cuba.cli.plugin.sdk.dto.Component
import com.haulmont.cuba.cli.plugin.sdk.nexus.NexusManager
import org.kodein.di.generic.instance

abstract class BaseRemoveCommand : BaseComponentCommand() {

    internal val nexusManager: NexusManager by sdkKodein.instance<NexusManager>()

    @Parameter(names = ["--local-only"], description = "Do not remove from local repository", hidden = true)
    var localOnly: Boolean = false
        private set

    override fun run() {
        var removeFromRepository = !localOnly
        if (!nexusManager.isStarted() && nexusManager.isLocal()) {
            val answers = Prompts.create {
                confirmation("remove.needToStartRepo", messages["remove.needToStartRepo"]) {
                    default(true)
                }
            }.ask()
            removeFromRepository = answers["remove.needToStartRepo"] as Boolean
            if (removeFromRepository) {
                StartCommand().execute()
            }
        }

        createSearchContext()?.let {
            val component = searchInMetadata(it)
            if (component != null) {
                remove(component, removeFromRepository)
                printWriter.println()
                printWriter.println(messages["removed"].green())
            } else {
                printWriter.println(messages["resolve.failed"].red())
            }
        }
    }

    internal fun remove(component: Component, removeFromRepository: Boolean) {
        componentManager.remove(component, removeFromRepository) { artifact, removed, total ->
            printProgress(
                messages["remove.removeDependency"].format(artifact.mvnCoordinates()),
                calculateProgress(removed, total)
            )
        }
        printWriter.println(messages["remove.finished"].green())

    }
}