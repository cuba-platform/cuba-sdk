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

import com.google.gson.Gson
import com.haulmont.cli.core.prompting.Option
import com.haulmont.cuba.cli.plugin.sdk.SdkPlugin
import com.haulmont.cuba.cli.plugin.sdk.commands.artifacts.NameVersion
import com.haulmont.cuba.cli.plugin.sdk.dto.Component
import com.haulmont.cuba.cli.plugin.sdk.dto.spring.SpringComponent
import com.haulmont.cuba.cli.plugin.sdk.dto.spring.SpringComponentCategory
import com.haulmont.cuba.cli.plugin.sdk.dto.spring.SpringComponentsInfo


class SpringBootProvider : BintraySearchComponentProvider() {

    var springComponentsInfo: SpringComponentsInfo? = null

    override fun getType() = "boot"

    override fun getComponent(template: Component): Component {
        return template.copy(type = getType())
    }

    override fun resolveCoordinates(nameVersion: NameVersion): Component? {
        nameVersion.split(":").let {
            when (it.size) {
                3 -> return Component(
                    groupId = it[0],
                    artifactId = it[1],
                    version = it[2],
                    type = getType()
                )
                2 -> {
                    val mAddon = innerComponents()
                        ?.find { addon -> addon.id == it[0] }
                    if (mAddon != null) {
                        return mAddon.copy(version = it[1])
                    }
                    return null
                }
                else -> return null
            }
        }
    }

    override fun availableVersions(componentId: String?): List<Option<String>> {
        innerComponents()
            ?.find { addon -> addon.id == componentId }
            ?.let {
                return super.availableVersions("${it.groupId}:${it.artifactId}")
            }
        return emptyList()
    }

    override fun innerComponents(): List<Component>? {
        val components = mutableListOf<Component>()
        springComponentsInfo?.let { springComponentsInfo ->
            for (category in springComponentsInfo.initializr.dependencies) {
                for (springComponent in category.content) {
                    if (isStarter(springComponent)) {
                        val component = createComponent(springComponent, category)
                        components.add(component)
                    }
                }
            }
        }
        return components
    }

    private fun createComponent(
        springComponent: SpringComponent,
        category: SpringComponentCategory
    ): Component {
        val groupId: String =
            if (!isStarter(springComponent)) springComponent.groupId!! else "org.springframework.boot"
        val artifactId: String =
            if (!isStarter(springComponent)) springComponent.artifactId!! else "spring-boot-starter-${springComponent.id}"
        val name: String =
            if (!isStarter(springComponent)) springComponent.name else "${springComponent.name}"
        val component = Component(
            groupId = groupId,
            artifactId = artifactId,
            version = "",
            type = getType(),
            id = springComponent.id,
            name = name,
            description = springComponent.description,
            category = category.name
        )
        return component
    }

    private fun isStarter(springComponent: SpringComponent) =
        springComponent.starter == null || springComponent.starter

    override fun load() {
        springComponentsInfo = Gson().fromJson(readSpringFile(), SpringComponentsInfo::class.java)
    }

    private fun readSpringFile(): String {
        return SdkPlugin::class.java.getResourceAsStream("spring-components.json")
            .bufferedReader()
            .use { it.readText() }
    }


}