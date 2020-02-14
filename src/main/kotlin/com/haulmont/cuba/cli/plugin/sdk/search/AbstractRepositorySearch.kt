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

import com.github.kittinunf.fuel.core.Request
import com.github.kittinunf.fuel.httpGet
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.haulmont.cuba.cli.plugin.sdk.dto.Component
import com.haulmont.cuba.cli.plugin.sdk.dto.Repository
import com.haulmont.cuba.cli.plugin.sdk.utils.Headers
import com.haulmont.cuba.cli.plugin.sdk.utils.authorizeIfRequired
import com.haulmont.cuba.cli.plugin.sdk.utils.header
import java.util.logging.Logger

abstract class AbstractRepositorySearch : RepositorySearch {

    internal val log: Logger = Logger.getLogger(BintraySearch::class.java.name)
    internal var repository: Repository

    constructor(repository: Repository) {
        this.repository = repository
    }

    abstract fun searchParameters(component: Component): List<Pair<String, String>>

    override fun search(component: Component): Component? {
        val result = createSearchRequest(repository.url, component)
            .responseString()
            .third
        val (data, error) = result

        if (error != null) {
            log.severe("error: ${error}")
            return null
        }

        result.fold(
            success = {
                log.info("Component found in ${repository}: ${component}")
                return handleResultJson(Gson().fromJson<JsonElement>(it, JsonElement::class.java), component)
            },
            failure = { error ->
                log.info("Component not found in ${repository}: ${component}")
                return null
            }
        )
    }

    abstract fun handleResultJson(
        it: JsonElement,
        component: Component
    ): Component?

    internal fun createSearchRequest(searchUrl: String, component: Component): Request {
        return searchUrl.httpGet(searchParameters(component))
            .authorizeIfRequired(repository)
            .header(Headers.CONTENT_TYPE, "application/json")
            .header(Headers.ACCEPT, "application/json")
            .header(Headers.CACHE_CONTROL, "no-cache")
    }

    internal fun componentAlreadyExists(componentsList: Collection<Component>, toAdd: Component): Component? =
        componentsList.filter {
            it.packageName == toAdd.packageName
                    && it.name == toAdd.name
                    && it.version == toAdd.version
                    && it.type == toAdd.type
        }.firstOrNull()
}