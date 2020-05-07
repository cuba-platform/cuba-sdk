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

package com.haulmont.cuba.cli.plugin.sdk.templates

import com.haulmont.cuba.cli.cubaplugin.di.sdkKodein
import com.haulmont.cuba.cli.plugin.sdk.commands.AbstractSdkCommand
import com.haulmont.cuba.cli.plugin.sdk.commands.artifacts.NameVersion
import com.haulmont.cuba.cli.plugin.sdk.dto.Classifier
import com.haulmont.cuba.cli.plugin.sdk.dto.Component
import com.haulmont.cuba.cli.plugin.sdk.dto.MarketplaceAddon
import com.haulmont.cuba.cli.plugin.sdk.dto.MvnArtifact
import com.haulmont.cuba.cli.plugin.sdk.services.ArtifactManager
import com.haulmont.cuba.cli.plugin.sdk.services.ComponentVersionManager
import com.haulmont.cuba.cli.prompting.Option
import org.apache.maven.model.Dependency
import org.kodein.di.generic.instance
import java.util.logging.Logger

class CubaAddonProvider : CubaProvider() {

    internal val componentVersionsManager: ComponentVersionManager by sdkKodein.instance<ComponentVersionManager>()
    internal val artifactManager: ArtifactManager by sdkKodein.instance<ArtifactManager>()
    internal val componentRegistry: ComponentRegistry by sdkKodein.instance<ComponentRegistry>()

    private val log: Logger = Logger.getLogger(CubaAddonProvider::class.java.name)

    override fun getType() = "addon"

    override fun getComponent(template: Component): Component {
        return search(
            template.copy(
                type = getType(),
                components = addonComponents(template.groupId, template.artifactId, template.version)
            )
        )
    }

    override fun innerComponents() = componentVersionsManager
        .addons()
        .sortedBy { it.id }
        .map { initAddonTemplate(it, "\${version}") }
        .toList()

    private fun initAddonTemplate(addon: MarketplaceAddon, version: String): Component {
        val packageName = addon.groupId
        val name = addon.artifactId.substringBefore("-global")
        val componentName = addon.id
        return Component(
            groupId = addon.groupId,
            artifactId = name,
            version = version,
            type = getType(),
            id = addon.id,
            name = addon.name,
            description = addon.description,
            category = addon.category,
            components = addonComponents(packageName, name, version)
        ).apply {
            if (componentName == "bproc") {
                this.components.add(Component(packageName, "$name-modeler", version))
            }
        }
    }

    private fun addonComponents(packageName: String, name: String, version: String): MutableSet<Component> {
        return mutableSetOf(
            Component(packageName, "$name-global", version),
            Component(packageName, "$name-gui", version),
            Component(packageName, "$name-portal", version),
            Component(packageName, "$name-core", version).apply {
                classifiers.add(Classifier("db", "zip"))
            },
            Component(packageName, "$name-web", version).apply {
                classifiers.addAll(
                    listOf(
                        Classifier.javadoc(),
                        Classifier("themes"),
                        Classifier("web", "zip")
                    )
                )
            },
            Component(packageName, "$name-web-themes", version),
            Component(packageName, "$name-web-toolkit", version).apply {
                classifiers.add(Classifier.client())
            }
        )
    }

    override fun availableVersions(componentId: String?) = componentVersionsManager.addons()
        .filter {
            it.id == componentId
        }
        .flatMap { it.compatibilityList }
        .flatMap {
            it.artifactVersions.map { version ->
                Option(
                    version,
                    AbstractSdkCommand.rootMessages["framework.cuba.version"].format(version, it.platformRequirement),
                    version
                )
            }
        }
        .sortedByDescending { it.value }
        .toList()

    override fun resolveCoordinates(nameVersion: NameVersion): Component? {
        nameVersion.split(":").let {
            when (it.size) {
                3 -> return Component(
                    groupId = it[0],
                    artifactId = it[1].substringBefore("-global"),
                    version = it[2],
                    type = getType()
                )
                2 -> {
                    val mAddon = componentVersionsManager.addons()
                        .find { addon -> addon.id == it[0] }
                    if (mAddon != null) {
                        return initAddonTemplate(mAddon, it[1])
                    }
                    return null
                }
                else -> return null
            }
        }
    }

    override fun searchAdditionalComponents(component: Component): Set<Component> {
        val additionalComponentList = mutableSetOf<Component>()
        component.globalModule()?.let {
            val model = artifactManager.readPom(
                MvnArtifact(
                    it.groupId,
                    it.artifactId,
                    it.version
                )
            )
            if (model == null) {
                log.info("Component not found: ${component}")
                throw IllegalStateException("Component not found: ${component}")
            }
            component.frameworkVersion =
                model.dependencies.filter { it.artifactId == "cuba-global" }.map { it.version }.firstOrNull()
            model.dependencies.filter { it.artifactId.endsWith("-global") }
                .forEach {
                    if (!it.artifactId.startsWith("cuba")) {
                        cubaAddon(it)?.let {
                            additionalComponentList.add(it)
                            additionalComponentList.addAll(searchAdditionalComponents(it))
                        }
                    }
                }
        }
        return additionalComponentList
    }

    private fun cubaAddon(dependency: Dependency): Component {
        return getComponent(
            Component(
                dependency.groupId,
                dependency.artifactId.substringBeforeLast("-global"),
                dependency.version
            )
        )
    }

    private fun cubaFramework(it: Dependency): Component {
        return componentRegistry.providerByName(CubaFrameworkProvider.CUBA_PLATFORM_PROVIDER)
            .getComponent(
                Component(
                    it.groupId,
                    it.artifactId.substringBeforeLast("-global"),
                    it.version
                )
            )
    }

    override fun load() {
        componentVersionsManager.load { }
    }


}