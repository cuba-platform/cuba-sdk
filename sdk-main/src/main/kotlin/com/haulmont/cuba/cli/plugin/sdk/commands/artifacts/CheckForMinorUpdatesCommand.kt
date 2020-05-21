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
import com.haulmont.cli.core.prompting.Option
import com.haulmont.cli.core.prompting.Prompts
import com.haulmont.cli.core.red
import com.haulmont.cuba.cli.plugin.sdk.di.sdkKodein
import com.haulmont.cuba.cli.plugin.sdk.dto.Component
import com.haulmont.cuba.cli.plugin.sdk.dto.Repository
import com.haulmont.cuba.cli.plugin.sdk.dto.RepositoryTarget
import com.haulmont.cuba.cli.plugin.sdk.event.SdkEvent
import com.haulmont.cuba.cli.plugin.sdk.templates.ComponentProvider
import com.haulmont.cuba.cli.plugin.sdk.templates.ComponentRegistry
import com.haulmont.cuba.cli.plugin.sdk.utils.doubleUnderline
import com.haulmont.cuba.cli.plugin.sdk.utils.splitVersion
import org.kodein.di.generic.instance
import kotlin.concurrent.thread

@Parameters(commandDescription = "Check for minor resolved component updates")
class CheckForMinorUpdatesCommand : BaseComponentCommand() {

    protected val registry: ComponentRegistry by sdkKodein.instance<ComponentRegistry>()

    @Parameter(
        names = ["--r", "--repository"],
        description = "Repository",
        hidden = true,
        variableArity = true
    )
    private var repositoryNames: List<String>? = null

    @Parameter(names = ["--no-upload"], description = "Do not upload components to repositories", hidden = true)
    var noUpload: Boolean = false
        private set

    override fun createSearchContext(): Component? {
        return null
    }

    override fun run() {

        val allUpdates = mutableListOf<Component>()

        for (provider in registry.providers()) {
            val updates = checkUpdates(provider)
            if (updates.isNotEmpty()) {
                printWriter.println(
                    messages["update.availableComponentUpdates"]
                        .format(provider.getType()).doubleUnderline()
                )
                updates.forEach { printWriter.println("$it") }
                printWriter.println()
                allUpdates.addAll(updates.sortedBy { it.toString() })
            }
        }

        if (allUpdates.isEmpty()) {
            printWriter.println(messages["update.noUpdatesFound"].green())
            return
        }

        val repositories: List<Repository>? = if (noUpload) emptyList() else
            repositories(repositoryNames ?: repositoryManager.getRepositories(RepositoryTarget.TARGET).map { it.name })

        if (!noUpload && repositories == null) {
            printWriter.println(messages["repository.noTargetRepositories"].red())
            return
        }

        val installAll = Prompts.create {
            confirmation("installAll", messages["update.installAll"]) {
                default(true)
            }
        }.ask()

        if (installAll["installAll"] as Boolean) {
            installComponents(allUpdates, repositories)
        } else {
            val versionAnswers = Prompts.create {
                options(
                    "updates",
                    messages["update.selectComponents"],
                    allUpdates.map {
                        Option(
                            it.toString(),
                            it.toString(),
                            it
                        )
                    }
                )
            }.ask()
            installComponents(listOf(versionAnswers["updates"] as Component), repositories)
        }
    }

    private fun checkUpdates(provider: ComponentProvider): Collection<Component> {
        val thread = thread {
            provider.load()
        }
        waitTask(messages["update.checkUpdates"].format(provider.getType())) {
            thread.isAlive
        }

        val components = metadataHolder.getResolved()
            .filter { provider.getType() == it.type }
        val availableUpdates = mutableListOf<Component>()
        components.forEach { component ->
            component.version.splitVersion()?.let { version ->
                val majorVersion = version.major
                val minorVersion = version.minor
                if (minorVersion != null && version.qualifier?.toLowerCase() != "snapshot") {
                    val newVersions = provider.availableVersions(component.id)
                        .mapNotNull { it.value.splitVersion() }
                        .filter { versionSplit ->
                            return@filter versionSplit.major == majorVersion
                                    && versionSplit.minor != null
                                    && versionSplit.minor > minorVersion
                        }.map { it.minor as Int }
                    if (newVersions.isNotEmpty()) {
                        val version = majorVersion + "." + newVersions.max()
                        val update =
                            Component(component.groupId, component.artifactId, version, type = component.type)
                        if (!updateAlreadyResolved(update)) {
                            bus.post(SdkEvent.NewVersionAvailableEvent(component, update))
                            availableUpdates.add(update)
                        }
                    }
                }
            }
        }
        return availableUpdates
    }

    private fun installComponents(updates: Collection<Component>, repositories: List<Repository>?) {
        val resolvedComponents = mutableListOf<Component>()
        updates.forEach {
            search(it)?.also {
                resolve(it)
                resolvedComponents.add(it)
            }
        }
        if (!noUpload) {
            upload(resolvedComponents, repositories!!)
        }
        printWriter.println(messages["update.componentsUpdated"].format(resolvedComponents).green())
    }

    private fun updateAlreadyResolved(update: Component): Boolean = searchInMetadata(update) != null
}