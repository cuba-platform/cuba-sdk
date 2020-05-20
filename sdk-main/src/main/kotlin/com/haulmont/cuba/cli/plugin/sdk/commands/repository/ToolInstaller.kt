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

import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.Request
import com.github.kittinunf.fuel.core.Response
import com.github.kittinunf.result.Result
import com.haulmont.cli.core.Messages
import com.haulmont.cli.core.PrintHelper
import com.haulmont.cli.core.commands.CommonParameters
import com.haulmont.cuba.cli.plugin.sdk.commands.AbstractSdkCommand
import com.haulmont.cuba.cli.plugin.sdk.commands.AbstractSdkCommand.Companion.calculateProgress
import com.haulmont.cuba.cli.plugin.sdk.commands.AbstractSdkCommand.Companion.printProgress
import com.haulmont.cuba.cli.plugin.sdk.di.sdkKodein
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
    internal val printWriter: PrintWriter by sdkKodein.instance<PrintWriter>()
    internal val sdkSettings: SdkSettingsHolder by sdkKodein.instance<SdkSettingsHolder>()
    internal val rootMessages = Messages(AbstractSdkCommand::class.java)

    fun downloadAndConfigure(configure: (Path) -> Unit, onFail: (Exception) -> Unit) {
        try {
            download().also {
                if (pathIsEmpty()) {
                    Files.createDirectory(installPath)
                }
                unzip(it)
            }.also {
                configure(it)
            }
        } catch (e: Exception) {
            onFail(e)
            if (CommonParameters.stacktrace) {
                PrintHelper().handleCommandException(e)
            }
        }
    }

    protected fun unzip(zipFilePath: Path) {
        val zipFilePath = beforeUnzip(zipFilePath)
        FileUtils.unzip(zipFilePath, installPath, skipFirstZipEntry) { count, total ->
            printProgress(
                AbstractSdkCommand.rootMessages["unzipProgress"],
                calculateProgress(count, total)
            )
        }
        onUnzipFinished()
    }

    open fun beforeUnzip(zipFilePath: Path) = zipFilePath

    open fun onUnzipFinished() {

    }

    private fun pathIsEmpty(): Boolean {
        return !Files.exists(installPath)
    }

    private fun download(): Path {
        val archive = sdkSettings.sdkHome().resolve("${name.toLowerCase()}.zip")
        if (!Files.exists(archive)) {
            printProgress(
                rootMessages["setup.download"].format(name),
                calculateProgress(0, 1)
            )

            var (_, _, result) = FileUtils.downloadFile(downloadLink, archive) { readBytes, totalBytes ->
                printProgress(
                    rootMessages["setup.download"].format(name),
                    calculateProgress(readBytes, totalBytes)
                )
            }

            result.fold(
                success = {
                    if (it.isEmpty()) {
                        printWriter.println()
                        throw IllegalStateException("Unable to download file from %s".format(downloadLink))
                    }
                    return archive
                },
                failure = {
                    printWriter.println()
                    throw IllegalStateException(it.message)
                })
        }
        return archive
    }

    private fun downloadArchive(
        downloadPath: String,
        archive: Path
    ): Triple<Request, Response, Result<ByteArray, FuelError>> {
        return Fuel.download(downloadPath).destination { response, Url ->
            archive.toFile()
        }.progress { readBytes, totalBytes ->
            printProgress(
                rootMessages["setup.download"].format(name),
                calculateProgress(readBytes, totalBytes)
            )
        }.response()
    }

}