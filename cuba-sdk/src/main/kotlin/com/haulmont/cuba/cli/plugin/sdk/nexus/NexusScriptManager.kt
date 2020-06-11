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
import org.json.JSONObject

interface NexusScriptManager {

    fun loadScript(name:String):String

    fun create(login: String, password: String, name: String, script: String, type: String = "groovy"): Response

    fun run(login: String, password: String, name: String, jsonObject: JSONObject? = null): Response

    fun drop(login: String, password: String, name: String): Response
}