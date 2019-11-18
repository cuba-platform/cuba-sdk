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

import com.haulmont.cuba.cli.plugin.sdk.SdkPlugin.Companion.SDK_PATH
import java.io.FileInputStream
import java.io.FileWriter
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.*

class SdkSettingsHolderImpl : SdkSettingsHolder {

    val SDK_PROPERTIES_PATH = SDK_PATH.resolve("sdk.properties")

    private val applicationProperties by lazy {
        val properties = Properties()

        createSdkPropertiesFileIfNotExists()

        val propertiesInputStream = FileInputStream(SDK_PROPERTIES_PATH.toString())
        propertiesInputStream.use {
            val inputStreamReader = InputStreamReader(propertiesInputStream, StandardCharsets.UTF_8)
            properties.load(inputStreamReader)
        }

        properties
    }

    override fun getProperty(property: String): String {
        return applicationProperties.getProperty(property)
    }

    override fun setProperty(property: String, value: String) {
        applicationProperties.put(property, value)
    }

    override fun flushAppProperties() {
        createSdkPropertiesFileIfNotExists()

        FileWriter(SDK_PROPERTIES_PATH.toString()).use {
            applicationProperties.store(it, "SDK properties")
        }

    }

    override fun sdkConfigured(): Boolean {
        return Files.exists(SDK_PROPERTIES_PATH) && applicationProperties.getProperty("repoType")!=null
    }


    private fun createSdkPropertiesFileIfNotExists() {
        if (!Files.exists(SDK_PROPERTIES_PATH)) {
            Files.createFile(SDK_PROPERTIES_PATH)
        }
    }


}