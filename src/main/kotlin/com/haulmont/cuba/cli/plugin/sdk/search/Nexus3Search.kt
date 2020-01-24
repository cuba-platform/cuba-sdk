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
import com.haulmont.cuba.cli.plugin.sdk.dto.Classifier
import com.haulmont.cuba.cli.plugin.sdk.dto.Component
import com.haulmont.cuba.cli.plugin.sdk.dto.Repository

class Nexus3Search(repository: Repository) : AbstractRepositorySearch(repository) {

    override fun searchParameters(component: Component): List<Pair<String, String>> {
        return listOf(
            "group" to component.packageName,
            "name" to "*",
            "version" to component.version,
            "repository" to repository.repositoryName
        )
    }

    override fun handleResultJson(it: JsonElement, component: Component): Component? {
        if (!it.isJsonArray) return null
        val array = it as JsonArray
        if (array.size() == 0) {
            log.info("Unknown ${component.type}: ${component.packageName}")
            return null
        }
        val json = array.get(0) as JsonObject
        val itemsArray = json.getAsJsonArray("items")
        if (itemsArray.size() == 0) {
            log.info("Unknown version: ${component.version}")
            return null
        }
        val components = mutableListOf<Component>()
        itemsArray.map { it as JsonObject }
            .map { dataObj ->
                val groupId = dataObj.get("group").asString
                val artifactId = dataObj.get("name").asString
                val version = dataObj.get("version").asString
                val classifiers = dataObj.getAsJsonArray("assets")
                    .map { it as JsonObject }
                    .map { asset ->
                        val path = asset.get("path").asString
                        val classifierAndExtension = path.substringAfterLast("${groupId}-${version}")
                        val classifier = if (classifierAndExtension.isNotEmpty())
                            classifierAndExtension.substringAfter("-").substringBefore(".") else ""
                        val extension = classifierAndExtension.substringAfter(".")
                        Classifier(classifier, extension)
                    }
                    .toMutableList()
                return@map Component(groupId, artifactId, version, classifiers = classifiers)
            }
            .forEach {
                components.add(it)
            }
        val copy = component.copy()
        copy.components.clear()
        copy.components.addAll(components)
        log.info("Component found in ${repository}: ${copy}")
        return copy
    }
}