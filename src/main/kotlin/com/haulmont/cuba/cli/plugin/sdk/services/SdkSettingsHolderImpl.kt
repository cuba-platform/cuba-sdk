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

package com.haulmont.cuba.cli.plugin.sdk.services

import com.haulmont.cuba.cli.cubaplugin.di.sdkKodein
import com.haulmont.cuba.cli.plugin.sdk.SdkPlugin
import org.kodein.di.generic.instance
import java.io.FileInputStream
import java.io.FileWriter
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

class SdkSettingsHolderImpl : SdkSettingsHolder {

    internal val sdkSettings: SdkSettingsHolder by sdkKodein.instance()

    val SDK_PROPERTIES_PATH by lazy {
        Paths.get(
            System.getProperty(
                "user.home"
            ), ".haulmont", "cli", "sdk"
        ).resolve("sdk.properties")
    }

    private var sdkProperties: Properties

    init {
        sdkProperties = readDefaultProperties()
    }

    private fun readDefaultProperties(): Properties {
        createSdkPropertiesFileIfNotExists()
        return readProperties(
            FileInputStream(SDK_PROPERTIES_PATH.toString()),
            readApplicationProperties()
        )
    }

    private fun readApplicationProperties() =
        readProperties(SdkPlugin::class.java.getResourceAsStream("application.properties"))

    private fun readProperties(
        propertiesInputStream: InputStream,
        defaultProperties: Properties = Properties()
    ): Properties {
        val properties = Properties(defaultProperties)
        propertiesInputStream.use {
            val inputStreamReader = InputStreamReader(propertiesInputStream, StandardCharsets.UTF_8)
            properties.load(inputStreamReader)
        }
        return properties
    }

    override fun sdkHome(): Path = getSdkPath().also {
        if (!Files.exists(it)) {
            Files.createDirectories(it)
        }
    }

    override fun nexusRepositoryPath(): Path = Path.of(sdkSettings["repository.path"])
        .resolve("nexus3")
        .resolve("bin")
        .resolve("nexus")

    private fun getSdkPath() =
        if (sdkProperties.contains("sdk.home"))
            Path.of(getProperty("sdk.home"))
        else Paths.get(
            System.getProperty(
                "user.home"
            ), ".haulmont", "cli", "sdk"
        )


    override fun getProperty(property: String): String {
        return sdkProperties.getProperty(property)
    }

    override fun hasProperty(property: String): Boolean {
        return sdkProperties[property] != null
    }

    override fun setProperty(property: String, value: String?) {
        if (value!=null) {
            sdkProperties.put(property, value)
        }
    }

    override fun flushAppProperties() {
        createSdkPropertiesFileIfNotExists()

        FileWriter(SDK_PROPERTIES_PATH.toString()).use {
            sdkProperties.store(it, "SDK properties")
        }
    }

    override fun sdkConfigured(): Boolean {
        return Files.exists(SDK_PROPERTIES_PATH)
    }

    override fun setExternalProperties(file: Path) {
        sdkProperties = readProperties(
            FileInputStream(file.toString()),
            sdkProperties
        )
    }

    override fun resetProperties() {
        sdkProperties = readDefaultProperties()
    }

    override fun propertyNames(): Set<String> {
        return sdkProperties.stringPropertyNames()
    }

    private fun createSdkPropertiesFileIfNotExists() {
        if (!Files.exists(SDK_PROPERTIES_PATH)) {
            Files.createFile(SDK_PROPERTIES_PATH)
        }
    }


}