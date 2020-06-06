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

package com.haulmont.cli.plugin.sdk.component.cuba.services

import com.github.kittinunf.fuel.Fuel
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.haulmont.cli.core.commands.LaunchOptions
import com.haulmont.cli.plugin.sdk.component.cuba.providers.CubaFrameworkProvider
import com.haulmont.cuba.cli.plugin.sdk.SdkPlugin
import com.haulmont.cuba.cli.plugin.sdk.di.sdkKodein
import com.haulmont.cuba.cli.plugin.sdk.dto.MarketplaceAddon
import com.haulmont.cuba.cli.plugin.sdk.dto.MarketplaceAddonCompatibility
import com.haulmont.cuba.cli.plugin.sdk.services.SdkSettingsHolder
import com.haulmont.cuba.cli.plugin.sdk.templates.ComponentRegistry
import org.kodein.di.generic.instance
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.logging.Logger
import kotlin.concurrent.thread


class ComponentVersionManagerImpl : ComponentVersionManager {

    private val log: Logger = Logger.getLogger(ComponentVersionManagerImpl::class.java.name)

    private val sdkSettings: SdkSettingsHolder by sdkKodein.instance<SdkSettingsHolder>()

    private val componentRegistry: ComponentRegistry by sdkKodein.instance<ComponentRegistry>()

    var addons: List<MarketplaceAddon>? = null

    private fun loadSync(): List<MarketplaceAddon> {

        if (LaunchOptions.skipVersionLoading) {
            return readAddons(
                readAddonsFile()
            )
        } else {
            var triple = Fuel.get(sdkSettings["addon.marketplaceUrl"])
                .responseString()

            triple.third.fold(
                success = {
                    return readAddons(it)
                },
                failure = { error ->
                    log.severe("error: ${error}")
                    return readAddons(
                        readAddonsFile()
                    )
                }
            )
        }
    }

    private fun readAddonsFile(): String {
        if (sdkSettings.hasProperty("addons-file")) {
            return Path.of(sdkSettings["addons-file"]).toFile().readText(StandardCharsets.UTF_8)
        }
        return SdkPlugin::class.java.getResourceAsStream("app-components.json")
            .bufferedReader()
            .use { it.readText() }
    }

    private fun readAddons(json: String): List<MarketplaceAddon> {
        val array = Gson().fromJson(json, JsonObject::class.java) ?: return emptyList()
        val platformVersions =
            componentRegistry.providerByName(CubaFrameworkProvider.CUBA_PLATFORM_PROVIDER).availableVersions(null)
        return array.getAsJsonArray("appComponents")
            .map { it as JsonObject }
            .map { Gson().fromJson(it, MarketplaceAddon::class.java) }
            .filter { it.artifactId.isNotEmpty() }
            .map {
                if (it != null) {
                    if (isStandardCubaAddon(it)) {
                        it.compatibilityList = platformVersions.map { version ->
                            MarketplaceAddonCompatibility(version.id, listOf(version.id))
                        }
                    }
                }
                return@map it
            }
            .toList()
    }

    private fun isStandardCubaAddon(addon: MarketplaceAddon): Boolean {
        return addon.compatibilityList.first().artifactVersions.joinToString().contains("\$cubaVersion")
    }

    override fun addons(): List<MarketplaceAddon> =
        addons ?: loadSync().also { addons = it }

    override fun load(loadCompletedFun: (addons: List<MarketplaceAddon>) -> Unit) {
        thread {
            loadCompletedFun(loadSync().also { addons = it })
        }
    }
}