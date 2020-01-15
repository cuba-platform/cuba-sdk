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
import com.haulmont.cuba.cli.plugin.sdk.dto.Component
import com.haulmont.cuba.cli.plugin.sdk.dto.Repository
import org.json.JSONArray
import org.json.JSONObject

class BintraySearch(repository: Repository) : AbstractRepositorySearch(repository) {
    override fun searchParameters(component: Component): List<Pair<String, String>> = listOf(
        "g" to component.packageName,
        "a" to "*",
        "subject" to repository.repositoryName
    )

    override fun handleResultJson(it: FuelJson, component: Component): Component? {
        val array = it.array()
        if (array.isEmpty) {
            log.info("Unknown ${component.type}: ${component.packageName}")
            return null
        }
        val json = array.get(0) as JSONObject
        val versions = json.get("versions") as JSONArray
        if (!versions.contains(component.version)) {
            log.info("Unknown version: ${component.version}")
            return null
        }

        val systemIds = json.get("system_ids") as JSONArray
        val components = mutableListOf<Component>()
        systemIds.toList().stream()
            .map { it as String }
            .map {
                val split = it.split(":")
                return@map Component(split[0], split[1], component.version)
            }.forEach {
                componentAlreadyExists(component.components, it)?.let {
                    it.classifiers.addAll(it.classifiers)
                }
                components.add(it)
            }

        component.components.clear()
        component.components.addAll(components)

        log.info("Component found in ${repository}: ${component}")
        return component
    }

    private fun componentAlreadyExists(componentsList: Collection<Component>, toAdd: Component): Component? =
        componentsList.filter {
            it.packageName == toAdd.packageName
                    && it.name == toAdd.name
                    && it.version == toAdd.version
                    && it.type == toAdd.type
        }.firstOrNull()


}