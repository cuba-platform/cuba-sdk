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

import com.github.kittinunf.fuel.json.FuelJson
import com.haulmont.cuba.cli.plugin.sdk.dto.Classifier
import com.haulmont.cuba.cli.plugin.sdk.dto.Component
import com.haulmont.cuba.cli.plugin.sdk.dto.Repository
import org.json.JSONArray
import org.json.JSONObject

class Nexus2Search(repository: Repository) : AbstractRepositorySearch(repository) {
    override fun searchParameters(component: Component): List<Pair<String, String>> = listOf(
        "g" to component.packageName,
        "a" to (component.name ?: ""),
        "v" to component.version
    )

    override fun handleResultJson(it: FuelJson, component: Component): Component {
        val array = it.array()
        if (array.isEmpty) {
            throw IllegalStateException("Unknown ${component.type}: ${component.packageName}")
        }
        val json = array.get(0) as JSONObject
        val dataArray = json.get("data") as JSONArray
        if (dataArray.isEmpty) {
            throw IllegalStateException("Unknown version: ${component.version}")
        }
        dataArray
            .map { it as JSONObject }
            .map { dataObj ->
                val groupId = dataObj.getString("groupId")
                val artifactId = dataObj.getString("artifactId")
                val version = dataObj.getString("latestRelease")
                val classifiers = dataObj.getJSONArray("artifactHits")
                    .map { it as JSONObject }
                    .flatMap { artifactHit ->
                        return@flatMap artifactHit.getJSONArray("artifactLinks")
                            .map { it as JSONObject }
                            .map { classifier ->
                                Classifier(
                                    classifier.getString("classifier"),
                                    classifier.getString("extension")
                                )
                            }
                    }
                    .toMutableList()
                return@map Component(groupId, artifactId, version, classifiers = classifiers)
            }
            .forEach {
                if (!componentAlreadyExists(component.components, it)) {
                    component.components.add(it)
                }
            }
        log.info("Component found in ${repository}: ${component}")
        return component
    }
}