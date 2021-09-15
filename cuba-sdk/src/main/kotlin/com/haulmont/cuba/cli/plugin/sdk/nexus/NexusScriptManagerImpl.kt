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

package com.haulmont.cuba.cli.plugin.sdk.nexus

import com.github.kittinunf.fuel.core.Response
import com.github.kittinunf.fuel.core.extensions.authenticate
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.httpDelete
import com.github.kittinunf.fuel.httpPost
import com.haulmont.cuba.cli.plugin.sdk.SdkPlugin
import com.haulmont.cuba.cli.plugin.sdk.di.sdkKodein
import com.haulmont.cuba.cli.plugin.sdk.services.SdkSettingsHolder
import com.haulmont.cuba.cli.plugin.sdk.utils.Headers
import org.json.JSONObject
import org.kodein.di.generic.instance

class NexusScriptManagerImpl : NexusScriptManager {

    internal val sdkSettings: SdkSettingsHolder by sdkKodein.instance<SdkSettingsHolder>()

    override fun loadScript(name: String): String {
        SdkPlugin::class.java
            .getResourceAsStream("scripts/${name}")
            .bufferedReader()
            .use { return it.readText() }
    }

    override fun create(login: String, password: String, name: String, script: String, type: String): Response {
        val jsonObject = JSONObject()
            .put("name", name)
            .put("type", type)
            .put("content", script)
        val (_, response, _) =
            "${sdkSettings["repository.url"]}service/rest/v1/script"
                .httpPost()
                .authentication().basic(login, password)
                .header(Headers.ACCEPT, "application/json")
                .header(Headers.CONTENT_TYPE, "application/json")
                .body(jsonObject.toString())
                .response()
        return response
    }

    override fun run(login: String, password: String, name: String, jsonObject: JSONObject?): Response {
        val (_, response, _) =
            "${sdkSettings["repository.url"]}service/rest/v1/script/$name/run"
                .httpPost()
                .authentication().basic(login, password)
                .header(Headers.ACCEPT, "application/json")
                .header(Headers.CACHE_CONTROL, "no-cache")
                .header(Headers.CONTENT_TYPE, "application/json")
                .also { request ->
                    jsonObject?.let { request.body(jsonObject.toString()) }
                }
                .response()
        return response
    }

    override fun drop(login: String, password: String, name: String): Response {
        val (_, response, _) =
            "${sdkSettings["repository.url"]}service/rest/v1/script/${name}"
                .httpDelete()
                .authentication().basic(login, password)
                .header(Headers.ACCEPT, "application/json")
                .response()
        return response
    }
}