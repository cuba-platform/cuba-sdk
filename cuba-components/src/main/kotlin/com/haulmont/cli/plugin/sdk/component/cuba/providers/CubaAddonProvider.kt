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

package com.haulmont.cli.plugin.sdk.component.cuba.providers

import com.haulmont.cli.core.localMessages
import com.haulmont.cli.core.prompting.Option
import com.haulmont.cli.plugin.sdk.component.cuba.di.cubaComponentKodein
import com.haulmont.cli.plugin.sdk.component.cuba.dto.CubaComponent
import com.haulmont.cli.plugin.sdk.component.cuba.services.cuba.CubaComponentVersionManager
import com.haulmont.cuba.cli.plugin.sdk.commands.artifacts.NameVersion
import com.haulmont.cuba.cli.plugin.sdk.dto.*
import org.apache.maven.model.Dependency
import org.kodein.di.generic.instance
import java.util.logging.Logger

class CubaAddonProvider : CubaProvider() {

    internal val cubaComponentVersionsManager: CubaComponentVersionManager by cubaComponentKodein.instance<CubaComponentVersionManager>()

    internal val messages by localMessages()

    private val log: Logger = Logger.getLogger(CubaAddonProvider::class.java.name)

    override fun getType() = "cuba-addon"

    override fun getName() = "CUBA addon"

    override fun createFromTemplate(template: Component): Component? {
        val mAddon = searchInMarketplace(
            id = template.id, groupId = template.groupId, artifactId = template.artifactId
        )?.let { initAddonTemplate(it, template.version) }
        return search(
            mAddon ?: CubaComponent(template.groupId,
                template.artifactId,
                template.version,
                id = template.id,
                name = template.name,
                type = getType(),
                components = addonComponents(template.groupId, template.artifactId, template.version))
        )
    }

    override fun components() = cubaComponentVersionsManager
        .addons()
        .sortedBy { it.id }
        .map { initAddonTemplate(it, "\${version}") }
        .toList()

    private fun initAddonTemplate(addon: CubaMarketplaceAddon, version: String): Component {
        val packageName = addon.groupId
        val name = addon.artifactId.substringBefore("-global")
        val componentName = addon.id
        return CubaComponent(
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
            } else if (componentName == "maps") {
                this.components.add(Component(packageName, "$name-web-toolkit", version).apply {
                    classifiers.add(Classifier("web", "zip"))
                })
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

    override fun versions(componentId: String?) = cubaComponentVersionsManager.addons()
        .filter {
            it.id == componentId
        }
        .flatMap { it.compatibilityList }
        .flatMap {
            it.artifactVersions.map { version ->
                Option(
                    version,
                    "%s [CUBA %s]".format(version, it.platformRequirement),
                    version
                )
            }
        }
        .sortedByDescending { it.value }
        .toList()

    override fun resolveCoordinates(nameVersion: NameVersion): Component? {
        nameVersion.split(":").let {
            when (it.size) {
                3 -> {
                    val mAddon = searchInMarketplace(groupId = it[0], artifactId = it[1])
                    if (mAddon != null) {
                        return initAddonTemplate(mAddon, it[2])
                    } else {
                        return CubaComponent(
                            groupId = it[0],
                            artifactId = it[1].substringBefore("-global"),
                            version = it[2],
                            type = getType()
                        )
                    }
                }
                2 -> {
                    val mAddon = searchInMarketplace(it[0])
                    if (mAddon != null) {
                        return initAddonTemplate(mAddon, it[1])
                    }
                    return null
                }
                else -> return null
            }
        }
    }

    private fun searchInMarketplace(id: String? = null, groupId: String? = null, artifactId: String? = null) =
        cubaComponentVersionsManager.addons()
            .find { addon ->
                addon.id == id || (addon.groupId == groupId && addon.artifactId == artifactId)
                        || (addon.groupId == groupId && addon.artifactId == "$artifactId-global")
            }

    override fun searchAdditionalComponents(component: Component): Set<Component> {
        val additionalComponentList = mutableSetOf<Component>()
        (component as CubaComponent).globalModule()?.let { global ->
            val model = artifactManager.readPom(
                MvnArtifact(
                    global.groupId,
                    global.artifactId,
                    global.version
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

    private fun cubaAddon(dependency: Dependency): Component? {
        return createFromTemplate(
            Component(
                dependency.groupId,
                dependency.artifactId.substringBeforeLast("-global"),
                dependency.version
            )
        )
    }

    override fun load() {
        cubaComponentVersionsManager.load { }
    }


}