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
import com.haulmont.cuba.cli.plugin.sdk.commands.AbstractSdkCommand
import com.haulmont.cuba.cli.plugin.sdk.commands.CommonSdkParameters
import com.haulmont.cuba.cli.plugin.sdk.dto.Component
import com.haulmont.cuba.cli.plugin.sdk.dto.Repository
import com.haulmont.cuba.cli.plugin.sdk.services.ComponentManager
import com.haulmont.cuba.cli.plugin.sdk.services.ComponentVersionManager
import com.haulmont.cuba.cli.plugin.sdk.services.MetadataHolder
import com.haulmont.cuba.cli.plugin.sdk.services.RepositoryManager
import com.haulmont.cuba.cli.prompting.Prompts
import com.haulmont.cuba.cli.prompting.ValidationException
import org.kodein.di.generic.instance

typealias NameVersion = String

abstract class BaseComponentCommand : AbstractSdkCommand() {

    internal val componentManager: ComponentManager by sdkKodein.instance()

    internal val repositoryManager: RepositoryManager by sdkKodein.instance()

    internal val metadataHolder: MetadataHolder by sdkKodein.instance()

    internal val componentVersionsManager: ComponentVersionManager by sdkKodein.instance()

    internal val platformVersionsManager: PlatformVersionsManager by sdkKodein.instance()

    @Parameter(names = ["--print-maven"], description = "Print maven output", hidden = true)
    var printMaven: Boolean = false
        private set

    @Parameter(names = ["--force"], description = "Force resolve and upload component with dependencies", hidden = true)
    var force: Boolean = false
        private set

    @Parameter(
        names = ["--parallel"],
        description = "Resolve component dependencies in parallel",
        hidden = true
    )
    var parallel: Boolean = true
        private set

    override fun postExecute() {
        super.postExecute()
        CommonSdkParameters.reset()
    }

    override fun preExecute() {
        super.preExecute()
        CommonSdkParameters.printMaven = printMaven
        CommonSdkParameters.singleThread = printMaven || !parallel
    }

    abstract fun createSearchContext(): Component?

    internal fun upload(component: Component, repository: Repository?) {
        printWriter.println()
        printWriter.println("Uploading dependencies...")
        componentManager.upload(component, repository) { artifact, uploaded, total ->
            printWriter.print(
                printProgress(
                    messages["dependencyUploadProgress"].format(artifact.mvnCoordinates()),
                    uploaded / total * 100
                )
            )
        }
        printWriter.print(
            printProgress(messages["uploaded"], 100f)
        )
    }

    internal fun resolve(component: Component) {
        printWriter.println("Resolving dependencies...")
        componentManager.resolve(component) { resolvedComponent, resolved, total ->
            printWriter.print(
                printProgress(
                    messages["dependencyResolvingProgress"].format(resolvedComponent),
                    resolved / total * 100
                )
            )
        }
        printWriter.print(
            printProgress(messages["resolved"], 100f)
        )
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

    fun askNameVersion(
        nameVersion: NameVersion?,
        msgPrefix: String,
        names: List<String>,
        versions: (name: String) -> List<String>
    ): NameVersion {
        if (nameVersion == null) {
            val name = askName(msgPrefix, names)
            val version = askVersion(msgPrefix, versions, name)
            return "${name}:$version"
        }
        val split = nameVersion.split(nameVersion)
        if (split.size == 1) {
            val version = askVersion(msgPrefix, versions, nameVersion)
            return "${nameVersion}:$version"
        }
        return nameVersion
    }

    private fun askVersion(
        msgPrefix: String,
        versions: (name: String) -> List<String>,
        name: String
    ): String {
        val versionAnswers = Prompts.create {
            textOptions(
                "version",
                messages["$msgPrefix.version"],
                versions(name)
            )
        }.ask()

        val version = versionAnswers["version"] as String
        return version
    }

    private fun askName(msgPrefix: String, addons: List<String>): String {
        val nameAnswers = Prompts.create {
            textOptions("name", messages["$msgPrefix.name"], addons)
        }.ask()

        val name = nameAnswers["name"] as String
        return name
    }
}