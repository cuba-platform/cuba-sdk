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

package com.haulmont.cli.plugin.sdk.component.cuba.search

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.haulmont.cli.plugin.sdk.component.cuba.dto.CubaComponent
import com.haulmont.cuba.cli.plugin.sdk.dto.Classifier
import com.haulmont.cuba.cli.plugin.sdk.dto.Component
import com.haulmont.cuba.cli.plugin.sdk.dto.Repository

class Nexus2Search(repository: Repository) : AbstractRepositorySearch(repository) {
    override fun searchParameters(component: Component, searchUrl: String): List<Pair<String, String>> {
        return listOf(
            "g" to component.groupId,
            "a" to ((component as CubaComponent).globalModule()?.artifactId?.substringBefore("-global") ?: ""),
            "v" to component.version
        )
    }

    override fun handleResultJson(it: JsonElement, component: Component): Component? {
        val json = it as JsonObject

        if (json.entrySet().isEmpty()) {
            log.info("Unknown ${component.type}: ${component.groupId}")
            return null
        }

        val dataArray = json.get("data") as JsonArray
        if (dataArray.size() == 0) {
            log.info("Unknown version: ${component.version}")
            return null
        }
        dataArray
            .map { it as JsonObject }
            .map { dataObj ->
                val groupId = dataObj.get("groupId").asString
                val artifactId = dataObj.get("artifactId").asString
                (component as CubaComponent).globalModule()?.let {
                    val prefix = it.artifactId.substringBefore("-global")
                    if (!artifactId.startsWith(prefix)) {
                        return@map null
                    }
                }

                val version = dataObj.get("latestRelease").asString
                val classifiers = dataObj.getAsJsonArray("artifactHits")
                    .map { it as JsonObject }
                    .flatMap { artifactHit ->
                        return@flatMap artifactHit.getAsJsonArray("artifactLinks")
                            .map { it as JsonObject }
                            .map { classifier ->
                                Classifier(
                                    classifier.get("classifier")?.asString?:"",
                                    classifier.get("extension").asString
                                )
                            }
                    }
                    .toMutableSet()
                return@map Component(groupId, artifactId, version, classifiers = classifiers)
            }
            .filterNotNull()
            .forEach {
                if (componentAlreadyExists(component.components, it) == null) {
                    component.components.add(it)
                }
            }

        log.info("Component found in ${repository}: ${component}")
        return component
    }
}