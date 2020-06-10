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
import com.haulmont.cli.core.prompting.Option
import com.haulmont.cli.core.prompting.Prompts
import com.haulmont.cli.core.prompting.ValidationException
import com.haulmont.cli.core.red
import com.haulmont.cuba.cli.plugin.sdk.commands.AbstractSdkCommand
import com.haulmont.cuba.cli.plugin.sdk.commands.CommonSdkParameters
import com.haulmont.cuba.cli.plugin.sdk.di.sdkKodein
import com.haulmont.cuba.cli.plugin.sdk.dto.Component
import com.haulmont.cuba.cli.plugin.sdk.dto.Repository
import com.haulmont.cuba.cli.plugin.sdk.dto.RepositoryTarget
import com.haulmont.cuba.cli.plugin.sdk.dto.RepositoryType
import com.haulmont.cuba.cli.plugin.sdk.event.SdkEvent
import com.haulmont.cuba.cli.plugin.sdk.services.ComponentManager
import com.haulmont.cuba.cli.plugin.sdk.services.MetadataHolder
import com.haulmont.cuba.cli.plugin.sdk.services.RepositoryManager
import com.haulmont.cuba.cli.plugin.sdk.templates.ComponentProvider
import com.haulmont.cuba.cli.plugin.sdk.templates.ComponentRegistry
import com.haulmont.cuba.cli.plugin.sdk.utils.splitVersion
import org.kodein.di.generic.instance
import kotlin.concurrent.thread

typealias NameVersion = String

abstract class BaseComponentCommand : AbstractSdkCommand() {

    internal val componentManager: ComponentManager by sdkKodein.instance<ComponentManager>()

    internal val repositoryManager: RepositoryManager by sdkKodein.instance<RepositoryManager>()

    internal val metadataHolder: MetadataHolder by sdkKodein.instance<MetadataHolder>()

    internal val componentRegistry: ComponentRegistry by sdkKodein.instance<ComponentRegistry>()

    internal var needToFindDependentAddons: Boolean? = null

    @Parameter(names = ["--info"], description = "Print output", hidden = true)
    var info: Boolean = false
        private set

    @Parameter(
        names = ["--f", "--force"],
        description = "Force resolve and upload component with dependencies",
        hidden = true
    )
    var force: Boolean = false
        private set

    @Parameter(
        names = ["--nra", "--not-resolve-addons"],
        description = "Do not resolve additional addons",
        hidden = true
    )
    var notSearchAdditionalDependencies: Boolean? = false

    @Parameter(
        names = ["--single"],
        description = "Resolve component dependencies in parallel",
        hidden = true
    )
    var single: Boolean = false
        private set

    @Parameter(
        names = ["--o", "--option"],
        description = "Artifact resolver option",
        hidden = true,
        variableArity = true
    )
    internal var resolverOpts: List<String>? = null

    override fun postExecute() {
        super.postExecute()
        CommonSdkParameters.reset()
    }

    override fun preExecute() {
        super.preExecute()
        CommonSdkParameters.info = info
        CommonSdkParameters.singleThread = info || single
        CommonSdkParameters.resolverOptions = resolverOpts
    }

    abstract fun createSearchContext(): Component?

    internal fun upload(component: Component, repositories: List<Repository>) {
        upload(listOf(component), repositories)
    }

    internal fun upload(components: List<Component>, repositories: List<Repository>) {
        components.forEach { component ->
            printWriter.println(messages["upload.progress"].format(component))
            bus.post(SdkEvent.BeforePushEvent(component, repositories))
            componentManager.upload(component, repositories) { artifact, uploaded, total ->
                printProgress(
                    messages["upload.progress"].format(artifact.mvnCoordinates()),
                    calculateProgress(uploaded, total)
                )
            }
            bus.post(SdkEvent.AfterPushEvent(component, repositories))
        }
        printWriter.println(messages["upload.finished"].green())
    }

    internal fun resolve(component: Component) {
        resolve(listOf(component))
    }

    internal fun resolve(components: List<Component>) {
        components.forEach { component ->
            bus.post(SdkEvent.BeforeResolveEvent(component))
            componentManager.resolve(component) { resolvedComponent, resolved, total ->
                printProgress(
                    messages["resolve.progress"].format(component),
                    calculateProgress(resolved, total)
                )
            }
            bus.post(SdkEvent.AfterResolveEvent(component))
            componentManager.register(component)
        }
        printWriter.println(messages["resolve.finished"].green())
    }

    internal fun componentWithDependents(component: Component): List<Component> =
        mutableListOf(component).apply { addAll(componentManager.searchForAdditionalComponents(component)) }

    internal fun repositories(repositoryNames: List<String>?): List<Repository>? {
        repositoryNames ?: return null
        return repositoryNames.map { repositoryName ->
            val repository = repositoryManager.getRepository(repositoryName, RepositoryTarget.TARGET)
                ?: throw ValidationException(messages["repository.unknown"].format(repositoryName))
            if (!repositoryManager.isOnline(repository)) {
                val msg = if (RepositoryType.LOCAL == repository.type)
                    messages["repository.pathNotExists"]
                else messages["repository.isOffline"]
                throw ValidationException(msg.format(repositoryName, repository.url))
            }
            repository
        }
    }

    internal fun register(component: Component) {
        componentManager.register(component)
    }

    internal fun searchInMetadata(component: Component): Component? {
        return componentManager.searchInMetadata(component)
    }

    open fun search(component: Component): Component? {
        return componentRegistry.providerByName(component.type).getComponent(component)
    }

    fun fail(cause: String): Nothing = throw ValidationException(cause)

    fun parseComponents(nameVersions: String): Set<Component> {
        val components = mutableSetOf<Component>()
        val searchThread = thread {
            nameVersions.split(",").map { coordinate ->
                coordinate.split("-").let {
                    val component = componentRegistry.providerByName(it[0]).resolveCoordinates(it[1]) ?: fail(
                        messages["component.unknown"].format(
                            it[0], it[1]
                        )
                    )
                    search(component)?.let {
                        components.add(it)
                    }
                }
            }
        }
        waitTask(messages["search.searchComponents"]) {
            searchThread.isAlive
        }
        return components
    }

    fun askComponentsWithDependencies(resolved: Boolean = false): Set<Component> {
        val componentCoordinates = askComponentsList(resolved)
        val components = mutableSetOf<Component>()
        val searchThread = thread {
            componentCoordinates.forEach {
                search(it)?.let {
                    components.add(it)
                }
            }
        }
        waitTask(messages["search.searchComponents"]) {
            searchThread.isAlive
        }

        if (components.isNotEmpty()) {
            val additionalComponents = mutableSetOf<Component>()
            val searchAdditionalThread = thread {
                components.forEach { component ->
                    componentManager.searchForAdditionalComponents(component).let {
                        if (it.isNotEmpty()) {
                            it.forEach {
                                if (!components.contains(it) && !additionalComponents.contains(it)) {
                                    additionalComponents.add(it)
                                }
                            }
                        }
                    }
                }
            }
            waitTask(messages["search.searchAdditionalComponents"]) {
                searchAdditionalThread.isAlive
            }
            components.addAll(additionalComponents)
        }
        return components
    }

    fun askComponentsList(resolved: Boolean): List<Component> {
        val components = mutableListOf<Component>()
        var installNext = true
        while (installNext) {
            val providerNames = componentRegistry.providers().map { it.getType() }.joinToString(separator = "/")
            val nameVersionAnswer = Prompts.create {
                question("nameVersion", messages["base.askComponentCoordinates"].format(providerNames)) {
                    validate {
                        notEmpty(value)
                    }
                }
            }.ask()
            val componentCoordinates = nameVersionAnswer["nameVersion"] as String
            componentCoordinates.split(" ").let {
                val nameVersion = if (it.size > 1) it[1] else null

                val component = if (resolved)
                    providerResolvedSearchContext(nameVersion, componentRegistry.providerByName(it[0]))
                else
                    providerSearchContext(nameVersion, componentRegistry.providerByName(it[0]))

                if (component != null) {
                    components.add(component)
                } else {
                    printWriter.println(messages["base.error.unsupportedComponentType"].format(it[0]).red())
                }
            }
            val installNextAnswer = Prompts.create {
                confirmation("installNext", messages["base.askInstallNext"])
            }.ask()
            installNext = installNextAnswer["installNext"] as Boolean
        }
        return components
    }

    fun askNameVersion(
        nameVersion: NameVersion?,
        provider: ComponentProvider,
        components: List<Component>?,
        versions: (name: String) -> List<Option<String>>
    ): NameVersion {
        val type = provider.getType()

        if (nameVersion == null) {
            return if (components != null) {
                val idName = askName(provider.getName(), components)
                val version = askVersion(idName.first, idName.second, versions)
                "${idName.first.toLowerCase()}:$version"
            } else {
                val version = askVersion(provider.getType(), provider.getName(), versions)
                version
            }
        }
        val split = nameVersion.split(":")
        if (split.last().splitVersion() == null) {
            val version = askVersion(nameVersion, null, versions)
            return "${nameVersion.toLowerCase()}:$version"
        }
        return nameVersion
    }

    private fun askVersion(
        id: String,
        name: String?,
        versions: (name: String) -> List<Option<String>>
    ): String {
        val versionsList = versions(id)
        val displayName = name ?: id
        if (versionsList.isEmpty()) {
            return Prompts.create {
                question(
                    "version",
                    messages["ask.question.version"].format(displayName)
                ) {
                    validate {
                        notEmpty(value)
                    }
                }
            }.ask()["version"] as String
        } else {
            return Prompts.create {
                options(
                    "version",
                    messages["ask.version"].format(displayName),
                    versionsList
                )
            }.ask()["version"] as String
        }
    }

    private fun askName(msgPrefix: String, innerComponents: List<Component>): Pair<String, String?> {
        if (innerComponents.isEmpty()) {
            return Pair(Prompts.create {
                question(
                    "name",
                    messages["ask.question.name"].format(msgPrefix)
                ) {
                    validate {
                        notEmpty(value)
                    }
                }
            }.ask()["name"] as String, null)
        } else {
            if (innerComponents.map { it.category }.distinct().filterNotNull().isEmpty()) {
                val components = innerComponents
                    .filter { it.id != null }
                    .map { Option(it.id!!, it.name ?: it.id, it.id!!) }
                    .toList()
                val id = Prompts.create {
                    options("name", messages["ask.name"].format(msgPrefix), components)
                }.ask()["name"] as String
                val component = innerComponents.find { it.id == id }
                return if (component != null) {
                    Pair(id, component.name)
                } else {
                    Pair(id, null)
                }
            } else {
                val categories = innerComponents.map { it.category ?: "Others" }
                    .distinct()
                    .map { Option(it, it, it) }
                    .toList()
                val category = Prompts.create {
                    options("category", messages["ask.category"].format(msgPrefix), categories)
                }.ask()["category"] as String
                val components = innerComponents
                    .filter { it.id != null }
                    .filter { it.category == if (category == "Others") null else category }
                    .map { Option(it.id!!, it.name ?: it.id, it.id!!) }
                    .toList()
                val id = Prompts.create {
                    options("name", messages["ask.name"].format(msgPrefix), components)
                }.ask()["name"] as String
                val component = innerComponents.find { it.id == id }
                return if (component != null) {
                    Pair(id, component.name)
                } else {
                    Pair(id, null)
                }

            }
        }
    }

    internal fun checkRepositories(repositoryNames: List<String>?): List<Repository>? {
        val repositories: List<Repository>? =
            repositories(repositoryNames ?: repositoryManager.getRepositories(RepositoryTarget.TARGET).map { it.name })

        if (repositories == null) {
            printWriter.println(messages["repository.noTargetRepositories"].red())
        }
        return repositories
    }

    internal fun providerSearchContext(nameVersion: NameVersion?, provider: ComponentProvider): Component? {
        return provider.resolveCoordinates(
            askNameVersion(
                nameVersion,
                provider,
                provider.innerComponents()
            ) { name ->
                return@askNameVersion provider.availableVersions(name)
            })
    }

    internal fun providerResolvedSearchContext(nameVersion: NameVersion?, provider: ComponentProvider): Component? {
        return provider.resolveCoordinates(
            askNameVersion(
                nameVersion,
                provider,
                provider.innerComponents()
                    ?.let { metadataHolder.getResolved().filter { it.type == provider.getType() }.toList() }
            ) { name ->
                val versions = ArrayList(provider.availableVersions(name))
                    .map { it.id to it }
                    .toMap()

                return@askNameVersion metadataHolder.getResolved()
                    .asSequence()
                    .filter { it.type == provider.getType() }
                    .filter { name.isEmpty() || it.id == name }
                    .map { it.version }
                    .distinct()
                    .map { versions.getOrElse(it) { Option(it, it, it) } }
                    .toList()
            })
    }

    internal fun notEmpty(value: String) {
        if (value.isEmpty()) {
            fail(messages["validation.notEmpty"])
        }
    }

    internal fun force(component: Component): Boolean =
        force || component.version.endsWith("-SNAPSHOT")

}