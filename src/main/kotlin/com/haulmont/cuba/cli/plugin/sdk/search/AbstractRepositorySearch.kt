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
import com.github.kittinunf.fuel.core.Request
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.fuel.json.FuelJson
import com.github.kittinunf.fuel.json.responseJson
import com.haulmont.cuba.cli.plugin.sdk.dto.Authentication
import com.haulmont.cuba.cli.plugin.sdk.dto.Component
import com.haulmont.cuba.cli.plugin.sdk.dto.Repository
import java.util.logging.Logger

abstract class AbstractRepositorySearch : RepositorySearch {

    internal val log: Logger = Logger.getLogger(BintraySearch::class.java.name)
    internal val repository: Repository

    constructor(repository: Repository) {
        this.repository = repository
    }

    abstract fun searchParameters(component: Component): List<Pair<String, String>>

    override fun search(component: Component): Component? {
        val result = createSearchRequest(repository.url, component)
            .responseJson()
            .third

        result.fold(
            success = {
                log.info("Component found in ${repository}: ${component}")
                return handleResultJson(it, component)
            },
            failure = { error ->
                log.info("Component not found in ${repository}: ${component}")
                return null
            }
        )
    }

    abstract fun handleResultJson(
        it: FuelJson,
        component: Component
    ): Component

    internal fun createSearchRequest(searchUrl: String, component: Component): Request {
        return searchUrl.httpGet(searchParameters(component))
            .authorizeIfRequired(repository)
            .header(Headers.CONTENT_TYPE, "application/json")
            .header(Headers.ACCEPT, "application/json")
            .header(Headers.CACHE_CONTROL, "no-cache")
    }

    fun Request.authorizeIfRequired(repository: Repository): Request {
        if (repository.authentication != null) {
            val authentication: Authentication = repository.authentication
            this.authentication().basic(authentication.login, authentication.password)
        }
        return this
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