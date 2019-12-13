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

import com.github.kittinunf.fuel.core.Headers
import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.fuel.json.responseJson
import com.haulmont.cuba.cli.plugin.sdk.dto.Component
import com.haulmont.cuba.cli.plugin.sdk.dto.SearchContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.logging.Logger

class BintraySearch : RepositorySearch {

    private val log: Logger = Logger.getLogger(BintraySearch::class.java.name)
    internal val searchContext: SearchContext

    constructor(searchContext: SearchContext) {
        this.searchContext = searchContext
    }

    override fun search(component: Component): Component? {
        val (_, _, result) = searchContext.url
            .httpGet(
                listOf(
                    "g" to component.packageName,
                    "a" to "${component.name}*",
                    "subject" to searchContext.subject
                )
            )
            .header(Headers.CONTENT_TYPE, "application/json")
            .header(Headers.ACCEPT, "application/json")
            .header(Headers.CACHE_CONTROL, "no-cache")
            .responseJson()

        result.fold(
            success = {
                val array = it.array()
                if (array.isEmpty) {
                    throw IllegalStateException("Unknown framework: ${component.packageName}")
                }
                val json = array.get(0) as JSONObject
                val versions = json.get("versions") as JSONArray
                if (!versions.contains(component.version)) {
                    throw IllegalStateException("Unknown version: ${component.version}")
                }

                val systemIds = json.get("system_ids") as JSONArray
                systemIds.toList().stream()
                    .map { it as String }
                    .map {
                        val split = it.split(":")
                        return@map Component(split[0], split[1], component.version)
                    }.forEach {
                        if (!componentAlreadyExists(component.components,it)) {
                            component.components.add(it)
                        }
                    }

                log.info("Component found in ${searchContext}: ${component}")
                return component
            },
            failure = { error ->
                log.info("Component not found in ${searchContext}: ${component}")
                return null
            }
        )
    }

    fun componentAlreadyExists(componentsList: Collection<Component>, toAdd: Component): Boolean {
        for (component in componentsList) {
            if (component.packageName == toAdd.packageName
                && component.name == toAdd.name
                && component.version == toAdd.version
                && component.type == toAdd.type
            ) {
                return true
            }
        }
        return false
    }

}