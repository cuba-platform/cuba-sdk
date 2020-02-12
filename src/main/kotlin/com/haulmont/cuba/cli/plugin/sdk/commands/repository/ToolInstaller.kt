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

package com.haulmont.cuba.cli.plugin.sdk.commands.repository

import com.haulmont.cuba.cli.Messages
import com.haulmont.cuba.cli.cubaplugin.di.sdkKodein
import com.haulmont.cuba.cli.plugin.sdk.commands.AbstractSdkCommand
import com.haulmont.cuba.cli.plugin.sdk.commands.AbstractSdkCommand.Companion.calculateProgress
import com.haulmont.cuba.cli.plugin.sdk.commands.AbstractSdkCommand.Companion.printProgress
import com.haulmont.cuba.cli.plugin.sdk.services.FileDownloadService
import com.haulmont.cuba.cli.plugin.sdk.services.SdkSettingsHolder
import com.haulmont.cuba.cli.plugin.sdk.utils.FileUtils
import org.kodein.di.generic.instance
import java.io.PrintWriter
import java.nio.file.Files
import java.nio.file.Path

open class ToolInstaller(
    val name: String,
    val downloadLink: String,
    val installPath: Path,
    val skipFirstZipEntry: Boolean = false
) {
    internal val fileDownloadService: FileDownloadService by sdkKodein.instance()
    internal val printWriter: PrintWriter by sdkKodein.instance()
    internal val sdkSettings: SdkSettingsHolder by sdkKodein.instance()
    internal val rootMessages = Messages(AbstractSdkCommand::class.java)

    fun downloadAndConfigure(configure: (Path) -> Unit) {
        download().also {
            if (pathIsEmpty()) {
                Files.createDirectory(installPath)
                unzip(it)
            }
        }.also {
            Files.delete(it)
            configure(it)
        }
    }

    protected fun unzip(it: Path) {
        beforeUnzip()
        FileUtils.unzip(it, installPath, skipFirstZipEntry) { count, total ->
            printProgress(
                AbstractSdkCommand.rootMessages["unzipProgress"],
                calculateProgress(count, total)
            )
        }
        onUnzipFinished()
    }

    open fun beforeUnzip() {

    }

    open fun onUnzipFinished() {

    }

    private fun pathIsEmpty(): Boolean {
        return !Files.exists(installPath)
    }

    private fun download(): Path {
        val archive = sdkSettings.sdkHome().resolve("${name.toLowerCase()}.zip")
        if (!Files.exists(archive)) {
            val file = Files.createFile(archive)
            fileDownloadService.downloadFile(
                downloadLink,
                file
            ) { bytesRead: Long, contentLength: Long, isDone: Boolean ->
                printProgress(
                    rootMessages["setup.download"].format(name),
                    calculateProgress(bytesRead, contentLength)
                )
            }
        }
        return archive
    }

}