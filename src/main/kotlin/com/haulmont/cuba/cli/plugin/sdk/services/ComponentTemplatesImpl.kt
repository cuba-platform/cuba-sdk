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
import com.google.gson.JsonObject
import com.haulmont.cuba.cli.cubaplugin.di.sdkKodein
import com.haulmont.cuba.cli.plugin.sdk.dto.Classifier
import com.haulmont.cuba.cli.plugin.sdk.dto.Classifier.Companion.default
import com.haulmont.cuba.cli.plugin.sdk.dto.Classifier.Companion.javadoc
import com.haulmont.cuba.cli.plugin.sdk.dto.Classifier.Companion.pom
import com.haulmont.cuba.cli.plugin.sdk.dto.Classifier.Companion.sdk
import com.haulmont.cuba.cli.plugin.sdk.dto.Classifier.Companion.sources
import com.haulmont.cuba.cli.plugin.sdk.dto.Component
import com.haulmont.cuba.cli.plugin.sdk.dto.ComponentType
import org.kodein.di.generic.instance
import java.nio.file.Files
import java.util.logging.Logger

class ComponentTemplatesImpl : ComponentTemplates {

    private val log: Logger = Logger.getLogger(ComponentTemplatesImpl::class.java.name)

    private val sdkSettings: SdkSettingsHolder by sdkKodein.instance()

    private val componentDescriptorFile by lazy {
        val file = sdkSettings.sdkHome.resolve("AppComponents11_1.json")
    }

    private val templates: List<Component> by lazy {
        val templates = ArrayList<Component>()
        templates.addAll(getBaseTemplates())

        val componentDescriptorFile = sdkSettings.sdkHome.resolve("AppComponents11_1.json")
//        val (_, response, _) = Fuel.download("http://www.cuba-platform.com/AppComponents11_1.json")
//            .fileDestination { response, Url ->
//                componentDescriptorFile.toFile()
//            }.response()

        if (Files.exists(componentDescriptorFile)) {
            componentDescriptorFile.toFile().readText().let {
                val array = Gson().fromJson(it, JsonObject::class.java)
                array.getAsJsonArray("appComponents").forEach {
                    val obj = it as JsonObject
                    templates.add(
                        initAddonTemplate(obj)
                    )
                }
            }
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
            "com.haulmont.cuba", "cuba", "\${version}", ComponentType.FRAMEWORK, components =
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
                Component("org.glassfish.jaxb", "jaxb-runtime", "2.3.1"),
                Component(
                    "org.apache.tomcat", "tomcat", "9.0.19", classifiers = mutableListOf(
                        pom(),
                        Classifier("", "zip")
                    )
                )
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

    private fun initAddonTemplate(obj: JsonObject): Component {
        val packageName = obj.get("groupId").asString
        val name = obj.get("artifactId").asString.substringBefore("-global")
        val componentName = obj.get("id").asString
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