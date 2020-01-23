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

package com.haulmont.cuba.cli.plugin.sdk.dto

import com.github.kittinunf.fuel.core.Request

open class Repository(
    val active: Boolean = true,
    val name: String,
    val type: RepositoryType,
    val url: String,
    val authentication: Authentication? = null,
    val repositoryName: String = ""
) {
    public fun Request.authorizeIfRequired(repository: Repository): Request {
        if (repository.authentication != null) {
            val authentication: Authentication = repository.authentication
            this.authenticate(authentication.login, authentication.password)
        }
        return this
    }
}

