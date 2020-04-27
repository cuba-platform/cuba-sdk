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
import com.haulmont.cuba.cli.cubaplugin.di.sdkKodein
import com.haulmont.cuba.cli.cubaplugin.model.PlatformVersionsManager
import com.haulmont.cuba.cli.green
import com.haulmont.cuba.cli.plugin.sdk.commands.AbstractSdkCommand
import com.haulmont.cuba.cli.plugin.sdk.commands.CommonSdkParameters
import com.haulmont.cuba.cli.plugin.sdk.dto.Component
import com.haulmont.cuba.cli.plugin.sdk.dto.Repository
import com.haulmont.cuba.cli.plugin.sdk.dto.RepositoryTarget
import com.haulmont.cuba.cli.plugin.sdk.dto.RepositoryType
import com.haulmont.cuba.cli.plugin.sdk.services.ComponentManager
import com.haulmont.cuba.cli.plugin.sdk.services.ComponentVersionManager
import com.haulmont.cuba.cli.plugin.sdk.services.MetadataHolder
import com.haulmont.cuba.cli.plugin.sdk.services.RepositoryManager
import com.haulmont.cuba.cli.prompting.Option
import com.haulmont.cuba.cli.prompting.Prompts
import com.haulmont.cuba.cli.prompting.ValidationException
import com.haulmont.cuba.cli.red
import org.kodein.di.generic.instance
import kotlin.concurrent.thread

typealias NameVersion = String

abstract class BaseComponentCommand : AbstractSdkCommand() {

    internal val componentManager: ComponentManager by sdkKodein.instance()

    internal val repositoryManager: RepositoryManager by sdkKodein.instance()

    internal val metadataHolder: MetadataHolder by sdkKodein.instance()

    internal val componentVersionsManager: ComponentVersionManager by sdkKodein.instance()

    internal val platformVersionsManager: PlatformVersionsManager by sdkKodein.instance()

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
        names = ["--go", "--gradle-option"],
        description = "Gradle option",
        hidden = true,
        variableArity = true
    )
    internal var gradleOpts: List<String>? = null

    override fun postExecute() {
        super.postExecute()
        CommonSdkParameters.reset()
    }

    override fun preExecute() {
        super.preExecute()
        CommonSdkParameters.info = info
        CommonSdkParameters.singleThread = info || single
        CommonSdkParameters.gradleOptions = gradleOpts
    }

    abstract fun createSearchContext(): Component?

    internal fun upload(component: Component, repositories: List<Repository>) {
        upload(listOf(component), repositories)
    }

    internal fun upload(components: List<Component>, repositories: List<Repository>) {
        components.forEach { component ->
            printWriter.println(messages["upload.progress"].format(component))
            componentManager.upload(component, repositories) { artifact, uploaded, total ->
                printProgress(
                    messages["upload.progress"].format(artifact.mvnCoordinates()),
                    calculateProgress(uploaded, total)
                )
            }
        }
        printWriter.println(messages["upload.finished"].green())
    }

    internal fun resolve(component: Component) {
        resolve(listOf(component))
    }

    internal fun resolve(components: List<Component>) {
        components.forEach { component ->
            componentManager.resolve(component) { resolvedComponent, resolved, total ->
                printProgress(
                    messages["resolve.progress"].format(component),
                    calculateProgress(resolved, total)
                )
            }
            componentManager.register(component)
        }
        printWriter.println(messages["resolve.finished"].green())
    }

    internal fun searchAdditionalComponents(
        component: Component
    ): Collection<Component> {
        if (notSearchAdditionalDependencies != true && needToFindDependentAddons != false) {
            printWriter.println(messages["search.searchAdditionalComponents"])
            componentManager.searchForAdditionalComponents(component).let {
                if (it.isNotEmpty()) {
                    printWriter.println(messages["search.foundAdditionalComponents"])
                    it.sortedBy { it.toString() }.forEach { component ->
                        printWriter.println("   $component")
                    }
                    printWriter.println()
                    if (needToFindDependentAddons == null) {
                        val answer = Prompts.create {
                            confirmation("resolve", messages["base.resolveAddonsCaption"])
                        }.ask()

                        needToFindDependentAddons = answer["resolve"] as Boolean
                    }

                    if (needToFindDependentAddons == true) {
                        return it
                    }
                }
            }
        }
        return emptyList()
    }

    internal fun componentWithDependents(component: Component): List<Component> =
        mutableListOf(component).apply { addAll(searchAdditionalComponents(component)) }


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
        return componentManager.search(component)
    }

    fun fail(cause: String): Nothing = throw ValidationException(cause)

    fun parseComponents(nameVersions: String): Set<Component> {
        val components = mutableSetOf<Component>()
        val searchThread = thread {
            nameVersions.split(",").map { coordinate ->
                coordinate.split("-").let {
                    val component = when (it[0]) {
                        "framework" -> it[1].resolveFrameworkCoordinates() ?: fail(
                            messages["framework.unknown"].format(
                                it[1]
                            )
                        )
                        "addon" -> it[1].resolveAddonCoordinates() ?: fail(
                            messages["addon.unknown"].format(
                                it[1]
                            )
                        )
                        "lib" -> it[1].resolveLibraryCoordinates()
                            ?: fail(messages["lib.unknown"].format(it[1]))
                        else -> null
                    }
                    if (component != null) {
                        search(component)?.let {
                            components.add(it)
                        }
                    }
                }
            }
        }
        waitTask(messages["search.searchComponents"]) {
            searchThread.isAlive
        }
        return components
    }

    fun askComponentsWithDependencies(): Set<Component> {
        val componentCoordinates = askComponentsList()
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

            if (additionalComponents.isNotEmpty()) {
                printWriter.println(messages["search.foundAdditionalComponents"])
                additionalComponents.sortedBy { it.toString() }.forEach { component ->
                    printWriter.println("   $component")
                }
                printWriter.println()
                if (needToFindDependentAddons == null) {
                    val answer = Prompts.create {
                        confirmation("resolve", messages["base.resolveAddonsCaption"])
                    }.ask()

                    needToFindDependentAddons = answer["resolve"] as Boolean
                }

                if (needToFindDependentAddons == true) {
                    components.addAll(additionalComponents)
                }
            }

        }
        return components
    }

    fun askComponentsList(): List<Component> {
        val components = mutableListOf<Component>()
        var installNext = true
        while (installNext) {
            val nameVersionAnswer = Prompts.create {
                question("nameVersion", messages["base.askComponentCoordinates"])
            }.ask()
            val componentCoordinates = nameVersionAnswer["nameVersion"] as String
            componentCoordinates.split(" ").let {
                val nameVersion = if (it.size > 1) it[1] else null
                val component = when (it[0]) {
                    "framework" -> askAllFrameworkNameVersion(nameVersion).resolveFrameworkCoordinates() ?: fail(
                        messages["framework.unknown"].format(
                            nameVersion
                        )
                    )
                    "addon" -> askAllAddonsNameVersion(nameVersion).resolveAddonCoordinates() ?: fail(
                        messages["addon.unknown"].format(
                            nameVersion
                        )
                    )
                    "lib" -> nameVersion?.resolveLibraryCoordinates()
                        ?: fail(messages["lib.unknown"].format(nameVersion))
                    else -> null
                }
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
        msgPrefix: String,
        names: List<String>,
        versions: (name: String) -> List<Option<String>>
    ): NameVersion {
        if (nameVersion == null) {
            val name = askName(msgPrefix, names)
            val version = askVersion(msgPrefix, versions, name)
            return "${name.toLowerCase()}:$version"
        }
        val split = nameVersion.split(":")
        if (split.size == 1) {
            val version = askVersion(msgPrefix, versions, nameVersion)
            return "${nameVersion.toLowerCase()}:$version"
        }
        return nameVersion
    }

    private fun askVersion(
        msgPrefix: String,
        versions: (name: String) -> List<Option<String>>,
        name: String
    ): String {
        val versionsList = versions(name)
        if (versionsList.isEmpty()) {
            return Prompts.create {
                question(
                    "version",
                    messages["$msgPrefix.question.version"]
                )
            }.ask()["version"] as String
        } else {
            return Prompts.create {
                options(
                    "version",
                    messages["$msgPrefix.version"],
                    versionsList
                )
            }.ask()["version"] as String
        }
    }

    private fun askName(msgPrefix: String, addons: List<String>): String {
        val nameAnswers = Prompts.create {
            textOptions("name", messages["$msgPrefix.name"], addons)
        }.ask()

        val name = nameAnswers["name"] as String
        return name
    }

    internal fun checkRepositories(repositoryNames: List<String>?): List<Repository>? {
        val repositories: List<Repository>? =
            repositories(repositoryNames ?: repositoryManager.getRepositories(RepositoryTarget.TARGET).map { it.name })

        if (repositories == null) {
            printWriter.println(messages["repository.noTargetRepositories"].red())
        }
        return repositories
    }

    internal fun force(component: Component): Boolean =
        force || component.version.endsWith("-SNAPSHOT")

}