package com.haulmont.cli.plugin.sdk.component.cuba.services.jmix

import com.github.kittinunf.fuel.Fuel
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.haulmont.cli.core.commands.LaunchOptions
import com.haulmont.cli.plugin.sdk.component.cuba.providers.JmixFrameworkProvider
import com.haulmont.cli.plugin.sdk.component.cuba.services.cuba.CubaComponentVersionManagerImpl
import com.haulmont.cuba.cli.plugin.sdk.SdkPlugin
import com.haulmont.cuba.cli.plugin.sdk.di.sdkKodein
import com.haulmont.cuba.cli.plugin.sdk.dto.JmixMarketplaceAddon
import com.haulmont.cuba.cli.plugin.sdk.dto.MarketplaceAddonCompatibility
import com.haulmont.cuba.cli.plugin.sdk.services.SdkSettingsHolder
import com.haulmont.cuba.cli.plugin.sdk.templates.ComponentRegistry
import org.kodein.di.generic.instance
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.logging.Logger
import kotlin.concurrent.thread

class JmixComponentVersionManagerImpl : JmixComponentVersionManager {

    private val log: Logger = Logger.getLogger(CubaComponentVersionManagerImpl::class.java.name)

    private val sdkSettings: SdkSettingsHolder by sdkKodein.instance<SdkSettingsHolder>()

    private val componentRegistry: ComponentRegistry by sdkKodein.instance<ComponentRegistry>()

    var addons: List<JmixMarketplaceAddon>? = null

    private fun loadSync(): List<JmixMarketplaceAddon> {

        if (LaunchOptions.skipVersionLoading) {
            return readAddons(
                readJmixAddonsFile()
            )
        } else {
            var triple = Fuel.get(sdkSettings["jmix.addon.marketplaceUrl"])
                .responseString()

            triple.third.fold(
                success = {
                    return readAddons(it)
                },
                failure = { error ->
                    log.severe("error: ${error}")
                    return readAddons(
                        readJmixAddonsFile()
                    )
                }
            )
        }
    }

    private fun readJmixAddonsFile(): String {
        if (sdkSettings.hasProperty("jmix-addons-file")) {
            return Path.of(sdkSettings["jmix-addons-file"]).toFile().readText(StandardCharsets.UTF_8)
        }
        return SdkPlugin::class.java.getResourceAsStream("jmix-app-components.json")
            .bufferedReader()
            .use { it.readText() }
    }

    private fun readAddons(json: String): List<JmixMarketplaceAddon> {
        val array = Gson().fromJson(json, JsonObject::class.java) ?: return emptyList()
        val platformVersions =
            componentRegistry.providerByName(JmixFrameworkProvider.JMIX_PLATFORM_PROVIDER).versions(null)
        return array.getAsJsonArray("appComponents")
            .map { it as JsonObject }
            .map {
                val addon = Gson().fromJson(it, JmixMarketplaceAddon::class.java)
                if (addon != null) {
                    addon.compatibilityList = platformVersions.map { version ->
                        MarketplaceAddonCompatibility(version.id, listOf(version.id))
                    }
                }
                return@map addon
            }
            .toList()
    }

    override fun addons(): List<JmixMarketplaceAddon> =
        addons ?: loadSync().also { addons = it }

    override fun load(loadCompletedFun: (addons: List<JmixMarketplaceAddon>) -> Unit) {
        thread {
            loadCompletedFun(loadSync().also { addons = it })
        }
    }
}