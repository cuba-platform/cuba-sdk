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

package com.haulmont.cuba.cli.plugin.sdk.services

import com.google.gson.Gson
import com.haulmont.cuba.cli.cubaplugin.di.sdkKodein
import com.haulmont.cuba.cli.generation.VelocityHelper
import com.haulmont.cuba.cli.plugin.sdk.dto.Classifier
import com.haulmont.cuba.cli.plugin.sdk.dto.Classifier.Companion.client
import com.haulmont.cuba.cli.plugin.sdk.dto.Classifier.Companion.default
import com.haulmont.cuba.cli.plugin.sdk.dto.Classifier.Companion.javadoc
import com.haulmont.cuba.cli.plugin.sdk.dto.Classifier.Companion.pom
import com.haulmont.cuba.cli.plugin.sdk.dto.Classifier.Companion.sdk
import com.haulmont.cuba.cli.plugin.sdk.dto.Classifier.Companion.sources
import com.haulmont.cuba.cli.plugin.sdk.dto.Component
import com.haulmont.cuba.cli.plugin.sdk.dto.ComponentType
import com.haulmont.cuba.cli.plugin.sdk.dto.MarketplaceAddon
import org.kodein.di.generic.instance
import java.util.logging.Logger

class ComponentTemplatesImpl : ComponentTemplates {

    private val log: Logger = Logger.getLogger(ComponentTemplatesImpl::class.java.name)

    private val componentManager: ComponentVersionManager by sdkKodein.instance()
    private val velocityHelper: VelocityHelper = VelocityHelper()

    private val templates: List<Component> by lazy {
        val templates = ArrayList<Component>()
        templates.addAll(getBaseTemplates())

        componentManager.addons().forEach {
            templates.add(initAddonTemplate(it))
        }

        return@lazy templates
    }

    override fun findTemplate(component: Component): Component? =
        getTemplates().searchTemplate(component)?.let {
            log.fine("Template for $component found")
            processComponentTemplate(component, it)
        }

    private fun processComponentTemplate(
        component: Component,
        template: Component
    ): Component? = Gson().fromJson<Component>(
        velocityHelper.generate(
            Gson().toJson(template), component.packageName,
            mapOf(
                "version" to component.version,
                "name" to (component.name ?: ""),
                "packageName" to component.packageName
            )
        ), Component::class.java
    )

    private fun Collection<Component>.searchTemplate(component: Component): Component? = find {
        matchTemplate(it, component)
    }

    private fun matchTemplate(it: Component, component: Component) =
        listOfNotNull(it.name, it.packageName)
            .intersect(
                listOfNotNull(
                    component.name,
                    component.packageName
                )
            ).isNotEmpty() && it.type == component.type

    private fun getBaseTemplates(): Collection<Component> {
        return listOf(
            cubaTemplate(),
            addonTemplate()
        )
    }

    private fun cubaTemplate(): Component {
        return Component(
            "com.haulmont.cuba",
            "cuba",
            "\${version}",
            type = ComponentType.FRAMEWORK,
            components = defaultComponents("com.haulmont.cuba", "cuba", "\${version}")
        ).apply {
            components.addAll(
                mutableSetOf(
                    Component(
                        "com.haulmont.cuba-resources", "cuba-png-icons", "1.0.1", classifiers = mutableListOf(
                            default(), pom()
                        )
                    ),
                    Component(
                        "com.haulmont.gradle", "cuba-plugin", "\${version}", classifiers = mutableListOf(
                            default(), pom(), sdk(), sources()
                        )
                    ),

                    Component("gradle.plugin.org.jetbrains.gradle.plugin.idea-ext", "gradle-idea-ext", "0.5"),
                    Component("javax.xml.bind", "jaxb-api", "2.3.1"),
                    Component("org.glassfish.jaxb", "jaxb-runtime", "2.3.1"),
                    Component("org.hsqldb", "hsqldb", "2.4.1")
                )
            )
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
                        javadoc(),
                        Classifier("themes"),
                        Classifier("web", "zip")
                    )
                )
            },
            Component(packageName, "$name-web-themes", version),
            Component(packageName, "$name-web-toolkit", version).apply {
                classifiers.add(client())
            }
        )
    }

    private fun defaultComponents(packageName: String, name: String, version: String): MutableSet<Component> {
        return mutableSetOf(
            Component(packageName, "$name-core", version).apply {
                classifiers.add(Classifier("db", "zip"))
            },
            Component(packageName, "$name-idp", version).apply {
                classifiers.add(Classifier("web"))
            },
            Component(packageName, "$name-web", version).apply {
                classifiers.addAll(
                    listOf(
                        javadoc(),
                        Classifier("themes"),
                        Classifier("web", "zip")
                    )
                )
            },
            Component(packageName, "$name-web-toolkit", version).apply {
                classifiers.addAll(
                    listOf(
                        client(),
                        Classifier("debug-client")
                    )
                )
            },
            Component(packageName, "$name-web-widgets", version).apply {
                classifiers.addAll(
                    listOf(
                        client(),
                        Classifier("debug-client")
                    )
                )
            },
            Component(packageName, "$name-web6", version).apply {
                classifiers.add(Classifier("web"))
            },
            Component(packageName, "$name-web6", version).apply {
                classifiers.add(Classifier("web"))
            },
            Component(
                packageName, "$name-web6-themes", version, classifiers = mutableListOf(
                    default(),
                    pom()
                )
            ),
            Component(packageName, "$name-web6-toolkit", version)
        )
    }

    private fun addonTemplate(): Component {
        return Component(
            packageName = "\${packageName}",
            name = "\${name}",
            version = "\${version}",
            type = ComponentType.ADDON,
            components = addonComponents("\${packageName}", "\${name}", "\${version}")
        )
    }

    private fun initAddonTemplate(addon: MarketplaceAddon): Component {
        val packageName = addon.groupId
        val name = addon.artifactId.substringBefore("-global")
        val componentName = addon.id
        return Component(
            packageName = packageName,
            name = componentName,
            version = "\${version}",
            type = ComponentType.ADDON,
            components = addonComponents(packageName, name, "\${version}")
        ).apply {
            if (componentName == "bproc") {
                this.components.add(Component(packageName, "$name-modeler", version))
            }
        }
    }

    override fun getTemplates(): Collection<Component> {
        return templates
    }
}