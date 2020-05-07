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

package com.haulmont.cuba.cli.plugin.sdk.search

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.haulmont.cuba.cli.plugin.sdk.dto.Component
import com.haulmont.cuba.cli.plugin.sdk.dto.Repository

class BintraySearch(repository: Repository) : AbstractRepositorySearch(repository) {
    override fun searchParameters(component: Component): List<Pair<String, String>> = listOf(
        "g" to component.groupId,
        "a" to if (component.globalModule() != null) component.globalModule()!!.artifactId.substringBefore("-global") + "*" else "*",
        "subject" to repository.repositoryName
    )

    override fun handleResultJson(it: JsonElement, component: Component): Component? {
        if (!it.isJsonArray) return null
        val array = it as JsonArray
        if (array.size() == 0) {
            log.info("Unknown ${component.type}: ${component.groupId}")
            return null
        }
        val json = array.get(0) as JsonObject
        val versions = json.getAsJsonArray("versions").map { it.asString }
        if (!versions.contains(component.version)) {
            log.info("Unknown version: ${component.version}")
            return null
        }
        val copy = component.copy()
        val systemIds = json.getAsJsonArray("system_ids")
        val components = mutableListOf<Component>()
        systemIds.toList().stream()
            .map { it.asString }
            .map {
                val split = it.split(":")
                val name = split[1]
                if (component.globalModule() != null) {
                    val prefix = component.globalModule()!!.artifactId.substringBefore("-global")
                    if (!name.startsWith(prefix)) {
                        return@map null
                    }
                }
                return@map Component(split[0], split[1], component.version)
            }
            .filter { it != null }
            .map { it as Component }
            .forEach {
                if (componentAlreadyExists(copy.components, it) == null) {
                    copy.components.add(it)
                }
            }

        log.info("Component found in ${repository}: ${copy}")
        return copy
    }




}