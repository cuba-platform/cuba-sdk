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
import com.haulmont.cuba.cli.green
import com.haulmont.cuba.cli.plugin.sdk.dto.Component
import com.haulmont.cuba.cli.plugin.sdk.dto.ComponentType
import com.haulmont.cuba.cli.plugin.sdk.dto.Repository
import com.haulmont.cuba.cli.plugin.sdk.dto.RepositoryTarget
import com.haulmont.cuba.cli.plugin.sdk.utils.doubleUnderline
import com.haulmont.cuba.cli.prompting.Option
import com.haulmont.cuba.cli.prompting.Prompts
import com.haulmont.cuba.cli.red
import java.lang.Integer.parseInt
import java.util.regex.Matcher
import java.util.regex.Pattern
import kotlin.concurrent.thread

@Parameters(commandDescription = "Check for minor resolved component updates")
class CheckForMinorUpdatesCommand : BaseComponentCommand() {

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


        val frameworkUpdates = checkFrameworkUpdates()
        if (frameworkUpdates.isNotEmpty()) {
            printWriter.println(messages["update.availableFrameworkUpdates"].doubleUnderline())
            frameworkUpdates.forEach { printWriter.println("$it") }
            printWriter.println()
        }

        val addonUpdates = checkAddonUpdates()
        if (addonUpdates.isNotEmpty()) {
            printWriter.println(messages["update.availableAddonUpdates"].doubleUnderline())
            addonUpdates.forEach { printWriter.println("$it") }
            printWriter.println()
        }

        val allUpdates = mutableListOf<Component>()
        allUpdates.addAll(frameworkUpdates.sortedBy { it.toString() })
        allUpdates.addAll(addonUpdates.sortedBy { it.toString() })
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

        val versionAnswers = Prompts.create {
            confirmation("installAll", messages["update.installAll"]) {
                default(true)
            }
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
            ) {
                askIf { it["installAll"] == false }
            }
        }.ask()

        if (versionAnswers["installAll"] as Boolean) {
            installComponents(allUpdates, repositories)
        } else {
            installComponents(listOf(versionAnswers["updates"] as Component), repositories)
        }
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

    private fun checkAddonUpdates(): Collection<Component> {
        val thread = thread {
            componentVersionsManager.load {}
        }
        waitTask(messages["update.checkAddons"]) {
            thread.isAlive
        }

        val addons = metadataHolder.getResolved()
            .filter { ComponentType.ADDON == it.type }
        val availableUpdates = mutableListOf<Component>()
        addons.forEach { addon ->
            val frameworkVersionSplit = splitVersion(addon.version)
            val majorVersion = frameworkVersionSplit.first
            val minorVersion = frameworkVersionSplit.second
            if (minorVersion != null && frameworkVersionSplit.third?.toLowerCase() != "snapshot") {
                val newVersions = componentVersionsManager.addons()
                    .filter { it.id == addon.name || "${it.groupId}.${it.artifactId}" == "${addon.packageName}.${addon.name}" }
                    .flatMap { it.compatibilityList }
                    .flatMap {
                        it.artifactVersions
                    }
                    .map { splitVersion(it) }.filter { versionSplit ->
                        return@filter versionSplit.first == majorVersion
                                && versionSplit.second != null
                                && versionSplit.second!! > minorVersion
                    }.map { it.second as Int }
                if (newVersions.isNotEmpty()) {
                    val version = majorVersion + "." + newVersions.max()
                    val update = Component(addon.packageName, addon.name, version, ComponentType.ADDON)
                    if (!updateAlreadyResolved(update)) {
                        availableUpdates.add(update)
                    }
                }
            }
        }
        return availableUpdates
    }

    private fun checkFrameworkUpdates(): Collection<Component> {
        val thread = thread {
            platformVersionsManager.load()
        }
        waitTask(messages["update.checkFrameworks"]) {
            thread.isAlive
        }

        val frameworks = metadataHolder.getResolved()
            .filter { ComponentType.FRAMEWORK == it.type }
        val availableUpdates = mutableListOf<Component>()
        frameworks.forEach { framework ->
            val frameworkVersionSplit = splitVersion(framework.version)
            val majorVersion = frameworkVersionSplit.first
            val minorVersion = frameworkVersionSplit.second
            if (minorVersion != null && frameworkVersionSplit.third?.toLowerCase() != "snapshot") {
                val newVersions = platformVersionsManager.versions.map { splitVersion(it) }.filter { versionSplit ->
                    return@filter versionSplit.first == majorVersion
                            && versionSplit.second != null
                            && versionSplit.second!! > minorVersion
                }.map { it.second as Int }
                if (newVersions.isNotEmpty()) {
                    val version = majorVersion + "." + newVersions.max()
                    val update = Component(framework.packageName, framework.name, version, ComponentType.FRAMEWORK)
                    if (!updateAlreadyResolved(update)) {
                        availableUpdates.add(update)
                    }
                }
            }
        }
        return availableUpdates
    }

    private fun updateAlreadyResolved(update: Component): Boolean = searchInMetadata(update) != null


    private fun splitVersion(version: String): Triple<String, Int?, String?> {
        val versionPattern: Pattern = Pattern.compile("([1-9]\\d*)\\.(\\d+)\\.(\\d+)(?:-([a-zA-Z0-9]+))?")
        val matcher: Matcher = versionPattern.matcher(version)

        if (matcher.matches()) {
            val majorVersion = matcher.group(1) + "." + matcher.group(2)
            val qualifier = matcher.group(4)
            val minorVersion = parseInt(matcher.group(3))
            return Triple(majorVersion, minorVersion, qualifier)
        }
        return return Triple(version, null, null)
    }
}