/*
 * Copyright (c) 2008-2020 Haulmont.
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
import com.haulmont.cli.core.green
import com.haulmont.cli.core.prompting.Answers
import com.haulmont.cli.core.prompting.Prompts
import com.haulmont.cli.core.prompting.QuestionsList
import com.haulmont.cuba.cli.plugin.sdk.commands.AbstractSdkCommand
import com.haulmont.cuba.cli.plugin.sdk.commands.repository.StartCommand
import com.haulmont.cuba.cli.plugin.sdk.di.sdkKodein
import com.haulmont.cuba.cli.plugin.sdk.dto.Component
import com.haulmont.cuba.cli.plugin.sdk.event.SdkEvent
import com.haulmont.cuba.cli.plugin.sdk.nexus.NexusManager
import com.haulmont.cuba.cli.plugin.sdk.nexus.NexusScriptManager
import com.haulmont.cuba.cli.plugin.sdk.services.ArtifactManager
import com.haulmont.cuba.cli.plugin.sdk.services.ComponentManager
import com.haulmont.cuba.cli.plugin.sdk.services.MetadataHolder
import com.haulmont.cuba.cli.plugin.sdk.services.RepositoryManager
import org.json.JSONObject
import org.kodein.di.generic.instance

@Parameters(commandDescription = "Clean SDK")
class CleanCommand : AbstractSdkCommand() {

    internal val nexusManager: NexusManager by sdkKodein.instance<NexusManager>()
    internal val repositoryManager: RepositoryManager by sdkKodein.instance<RepositoryManager>()
    internal val artifactManager: ArtifactManager by lazy { ArtifactManager.instance() }
    internal val nexusScriptManager: NexusScriptManager by sdkKodein.instance<NexusScriptManager>()
    internal val componentManager: ComponentManager by sdkKodein.instance<ComponentManager>()
    private val metadataHolder: MetadataHolder by sdkKodein.instance<MetadataHolder>()

    @Parameter(names = ["--local-only"], description = "Do not remove from local repository", hidden = true)
    var localOnly: Boolean = false
        private set

    override fun run() {
        Prompts.create(kodein) { askConfirmation() }
            .let(Prompts::ask)
            .let(this::cleanup)
    }

    private fun cleanup(answers: Answers) {
        if (answers["needToStartRepo"] != null && answers["needToStartRepo"] as Boolean) {
            StartCommand().execute()
        }
        if (answers["confirmed"] as Boolean) {
            artifactManager.clean()

            val resolvedComponents = metadataHolder.getResolved()
            val shallowComponentsCopy = resolvedComponents.toList()

            shallowComponentsCopy.forEach {
                bus.post(SdkEvent.BeforeRemoveEvent(it, false))
                componentManager.remove(it, false)
                bus.post(SdkEvent.AfterRemoveEvent(it, false))
            }

            if (nexusManager.isLocal() && nexusManager.isStarted() && !localOnly) {
                nexusScriptManager.run(
                    sdkSettings["repository.login"],
                    repositoryManager.getLocalRepositoryPassword() ?: "",
                    "sdk.cleanup",
                    JSONObject().put("repoName", sdkSettings["repository.name"])
                )
            }
        }

        printWriter.println(messages["cleanup.finished"].green())
    }

    private fun QuestionsList.askConfirmation() {
        confirmation("confirmed", messages["cleanup.confirmation"]) {
            default(false)
        }
        confirmation("needToStartRepo", messages["remove.needToStartRepo"]) {
            default(true)
            askIf {
                it["confirmed"] as Boolean && nexusManager.isLocal() && !nexusManager.isStarted() && !localOnly
            }
        }
    }
}