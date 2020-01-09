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

import com.haulmont.cuba.cli.cubaplugin.di.sdkKodein
import com.haulmont.cuba.cli.plugin.sdk.dto.Classifier
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

    private val templates: List<Component> by lazy {
        val templates = ArrayList<Component>()
        templates.addAll(getBaseTemplates())

        componentManager.addons().forEach {
            templates.add(initAddonTemplate(it))
        }

        return@lazy templates
    }

    private fun getBaseTemplates(): Collection<Component> {
        return listOf(
            cubaTemplate(),
            addonTemplate()
        )
    }

    private fun cubaTemplate(): Component {
        return Component(
            "com.haulmont.cuba", "cuba", "\${version}", type = ComponentType.FRAMEWORK, components =
            mutableSetOf(
                Component(
                    "com.haulmont.gradle", "cuba-plugin", "\${version}", classifiers = mutableListOf(
                        default(), pom(), sdk(), Classifier("sources")
                    )
                ),
                Component(
                    "com.haulmont.cuba", "cuba-core", "\${version}", classifiers = mutableListOf(
                        default(),
                        pom(),
                        sources(),
                        javadoc(),
                        Classifier("db", "zip")
                    )
                ),
                Component(
                    "com.haulmont.cuba", "cuba-web", "\${version}", classifiers = mutableListOf(
                        default(),
                        pom(),
                        sources(),
                        javadoc(),
                        Classifier("themes"),
                        Classifier("web", "zip")
                    )
                ),
                Component("gradle.plugin.org.jetbrains.gradle.plugin.idea-ext", "gradle-idea-ext", "0.5"),
                Component("javax.xml.bind", "jaxb-api", "2.3.1"),
                Component("org.glassfish.jaxb", "jaxb-runtime", "2.3.1")
            )
        )
    }

    private fun addonTemplate(): Component {
        return Component(
            packageName = "\${packageName}",
            name = "\${name}",
            version = "\${version}",
            type = ComponentType.ADDON,
            components =
            mutableSetOf(
                Component(
                    "\${packageName}", "\${name}-core", "\${version}", classifiers = mutableListOf(
                        default(),
                        pom(),
                        sources(),
                        javadoc(),
                        Classifier("db", "zip")
                    )
                ),
                Component(
                    "\${packageName}", "\${name}-web", "\${version}", classifiers = mutableListOf(
                        default(),
                        pom(),
                        sources(),
                        javadoc(),
                        Classifier("themes"),
                        Classifier("web", "zip")
                    )
                )
            )
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
            components =
            mutableSetOf(
                Component(
                    packageName, "${name}-core", "\${version}", classifiers = mutableListOf(
                        default(),
                        pom(),
                        sources(),
                        javadoc(),
                        Classifier("db", "zip")
                    )
                ),
                Component(
                    packageName, "${name}-web", "\${version}", classifiers = mutableListOf(
                        default(),
                        pom(),
                        sources(),
                        javadoc(),
                        Classifier("themes"),
                        Classifier("web", "zip")
                    )
                )
            )
        )
    }

    override fun getTemplates(): Collection<Component> {
        return templates
    }
}