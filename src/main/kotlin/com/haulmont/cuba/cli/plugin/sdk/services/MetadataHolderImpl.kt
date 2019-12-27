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

import com.google.gson.Gson
import com.haulmont.cuba.cli.plugin.sdk.SdkPlugin
import com.haulmont.cuba.cli.plugin.sdk.dto.SdkMetadata
import java.io.FileInputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardOpenOption

class MetadataHolderImpl : MetadataHolder {

    val SDK_METADATA_PATH = SdkPlugin.SDK_PATH.resolve("sdk.metadata")

    private val sdkMetadata by lazy {
        if (Files.exists(SDK_METADATA_PATH)) {
            FileInputStream(SDK_METADATA_PATH.toString())
                .bufferedReader(StandardCharsets.UTF_8)
                .use {
                    return@lazy Gson().fromJson(it.readText(), SdkMetadata::class.java)
                }
        } else {
            return@lazy initMetadata()
        }
    }

    private fun initMetadata(): SdkMetadata {
        return SdkMetadata().also {
//            it.repositories.add(
//                Repository(
//                    type = "bintray",
//                    url = "https://api.bintray.com/search/packages/maven?",
//                    repositoryName = "cuba-platform"
//                )
//            )
        }
    }

    override fun getMetadata(): SdkMetadata {
        return sdkMetadata
    }

    override fun flushMetadata() {
        createFileIfNotExists()
        Files.writeString(
            SDK_METADATA_PATH,
            Gson().toJson(sdkMetadata),
            StandardOpenOption.WRITE,
            StandardOpenOption.TRUNCATE_EXISTING
        )
    }

    private fun createFileIfNotExists() {
        if (!Files.exists(SDK_METADATA_PATH)) {
            Files.createFile(SDK_METADATA_PATH)
        }
    }

}