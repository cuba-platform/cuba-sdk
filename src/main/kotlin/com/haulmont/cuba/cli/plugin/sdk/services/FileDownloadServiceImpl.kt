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

import com.github.kittinunf.fuel.Fuel
import com.haulmont.cuba.cli.plugin.sdk.SdkPlugin.Companion.SDK_PATH
import java.io.File
import java.nio.file.Files

class FileDownloadServiceImpl : FileDownloadService {

    val SDK_TMP_PATH = SDK_PATH.resolve("tmp").also {
        if (!Files.exists(it)) {
            Files.createDirectories(it)
        }
    }

    override fun downloadFile(
        url: String,
        downloadFile: File,
        downloadProgressFun: (bytesRead: Long, contentLength: Long, isDone: Boolean) -> Unit
    ) {
        val (request, response, result) = Fuel.download(url).fileDestination { response, Url ->
            Files.createFile(SDK_TMP_PATH.resolve("nexus.zip")).toFile()
        }.progress { readBytes, totalBytes ->
            downloadProgressFun(readBytes, totalBytes, readBytes >= totalBytes)
        }.response()

        if (response.statusCode == 200) {
            downloadProgressFun(100, 100, true)
        }
    }


}