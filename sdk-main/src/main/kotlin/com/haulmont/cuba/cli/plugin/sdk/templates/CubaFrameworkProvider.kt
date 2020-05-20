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

import com.haulmont.cuba.cli.plugin.sdk.commands.artifacts.NameVersion
import com.haulmont.cuba.cli.plugin.sdk.di.sdkKodein
import com.haulmont.cuba.cli.plugin.sdk.dto.Classifier
import com.haulmont.cuba.cli.plugin.sdk.dto.Classifier.Companion.default
import com.haulmont.cuba.cli.plugin.sdk.dto.Classifier.Companion.javadoc
import com.haulmont.cuba.cli.plugin.sdk.dto.Classifier.Companion.pom
import com.haulmont.cuba.cli.plugin.sdk.dto.Classifier.Companion.sdk
import com.haulmont.cuba.cli.plugin.sdk.dto.Classifier.Companion.sources
import com.haulmont.cuba.cli.plugin.sdk.dto.Component
import com.haulmont.cuba.cli.plugin.sdk.dto.MvnArtifact
import com.haulmont.cuba.cli.plugin.sdk.services.ArtifactManager
import com.haulmont.cuba.cli.plugin.sdk.services.SdkSettingsHolder
import org.kodein.di.generic.instance

class CubaFrameworkProvider : CubaProvider() {

    internal val artifactManager: ArtifactManager by sdkKodein.instance<ArtifactManager>()
    internal val sdkSettings: SdkSettingsHolder by sdkKodein.instance<SdkSettingsHolder>()

    companion object {
        val CUBA_PLATFORM_PROVIDER = "cuba"
    }

    override fun getType() = CUBA_PLATFORM_PROVIDER

    override fun availableVersions(componentId: String?) = super.availableVersions("com.haulmont.cuba:cuba-global")

    override fun resolveCoordinates(nameVersion: NameVersion): Component? {
        nameVersion.split(":").let {
            when (it.size) {
                1 -> return Component(
                    groupId = "com.haulmont.cuba",
                    artifactId = "cuba",
                    version = it[0],
                    type = getType()
                )
                2 -> return Component(
                    groupId = "com.haulmont.cuba",
                    artifactId = "cuba",
                    version = it[1],
                    type = getType()
                )
                else -> return null
            }
        }
    }

    override fun load() {

    }

    override fun getComponent(template: Component) = search(Component(
        "com.haulmont.cuba",
        "cuba",
        template.version,
        type = getType(),
        id = "cuba",
        name = "CUBA",
        frameworkVersion = template.version,
        components = defaultComponents("com.haulmont.cuba", CUBA_PLATFORM_PROVIDER, template.version)
    ).apply {
        components.addAll(additionalPlatformLibs(template))
        components.addAll(sdkBomDependencies(template))
    })

    private fun sdkBomDependencies(template: Component): MutableList<Component> {
        val sdkBomDependencies = mutableListOf<Component>()
        val model = artifactManager.readPom(
            MvnArtifact("com.haulmont.gradle", "cuba-plugin", template.version),
            sdk()
        )
        if (model != null) {
            val tomcatVersion = model.properties["tomcat.version"] as String?
            if (tomcatVersion != null) {
                sdkBomDependencies.add(
                    Component(
                        "org.apache.tomcat", "tomcat", tomcatVersion, classifiers = mutableListOf(
                            pom(),
                            Classifier("", "zip")
                        )
                    )
                )
            }
            val gradleVersion = model.properties["gradle.version"] as String?
            if (gradleVersion != null) {
                sdkBomDependencies.add(
                    Component(
                        groupId = "gradle",
                        artifactId = "gradle",
                        url = sdkSettings["gradle.downloadLink"].format(gradleVersion),
                        version = gradleVersion,
                        classifiers = mutableListOf(Classifier("", "zip"))
                    )
                )
            }
        }
        return sdkBomDependencies
    }

    private fun additionalPlatformLibs(template: Component): MutableSet<Component> {
        return mutableSetOf(
            Component(
                "com.haulmont.cuba-resources", "cuba-png-icons", "1.0.1", classifiers = mutableListOf(
                    default(), pom()
                )
            ),
            Component(
                "com.haulmont.gradle", "cuba-plugin", template.version, classifiers = mutableListOf(
                    default(), pom(), sdk(), sources()
                )
            ),

            Component("gradle.plugin.org.jetbrains.gradle.plugin.idea-ext", "gradle-idea-ext", "0.5"),
            Component("javax.xml.bind", "jaxb-api", "2.3.1"),
            Component("org.glassfish.jaxb", "jaxb-runtime", "2.3.1"),
            Component("org.hsqldb", "hsqldb", "2.4.1")
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
                        Classifier.client(),
                        Classifier("debug-client")
                    )
                )
            },
            Component(packageName, "$name-web-widgets", version).apply {
                classifiers.addAll(
                    listOf(
                        Classifier.client(),
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
}