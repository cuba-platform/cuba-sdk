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

package com.haulmont.cuba.cli.plugin.sdk.services

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.haulmont.cuba.cli.cubaplugin.di.sdkKodein
import com.haulmont.cuba.cli.plugin.sdk.SdkPlugin
import com.haulmont.cuba.cli.plugin.sdk.dto.MarketplaceAddon
import org.kodein.di.generic.instance
import java.util.logging.Logger
import kotlin.concurrent.thread


class ComponentVersionManagerImpl : ComponentVersionManager {

    private val log: Logger = Logger.getLogger(ComponentVersionManagerImpl::class.java.name)

    private val sdkSettings: SdkSettingsHolder by sdkKodein.instance()

    var addons: List<MarketplaceAddon>? = null

    private fun loadSync(): List<MarketplaceAddon> {
        //TODO load addons from cuba-site
//        val result = Fuel.get(sdkSettings.getApplicationProperty("addonsMarketplaceUrl"))
//            .responseString().third
//
//        result.fold(
//            success = {
//                return readAddons(it)
//            },
//            failure = { error ->
//                log.severe("error: ${error}")
//            }
//        )

        return readAddons(
            SdkPlugin::class.java.getResourceAsStream("app-components.json")
                .bufferedReader()
                .use { it.readText() })
    }

    private fun readAddons(json: String): List<MarketplaceAddon> {
        val array = Gson().fromJson(json, JsonObject::class.java) ?: return emptyList()
        return array.getAsJsonArray("appComponents")
            .map { it as JsonObject }
            .map { Gson().fromJson(it, MarketplaceAddon::class.java) }
            .toList()
    }

    override fun addons(): List<MarketplaceAddon> =
        addons ?: loadSync().also { addons = it }

    override fun load(loadCompletedFun: (addons: List<MarketplaceAddon>) -> Unit) {
        thread {
            loadCompletedFun(loadSync().also { addons = it })
        }
    }
}