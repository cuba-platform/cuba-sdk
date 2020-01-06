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

package com.haulmont.cuba.cli.plugin.sdk.commands.repository

import com.beust.jcommander.Parameters
import com.github.kittinunf.fuel.httpHead
import com.haulmont.cuba.cli.commands.AbstractCommand
import com.haulmont.cuba.cli.cubaplugin.di.sdkKodein
import com.haulmont.cuba.cli.localMessages
import com.haulmont.cuba.cli.plugin.sdk.services.SdkSettingsHolder
import org.kodein.di.generic.instance
import java.io.PrintWriter
import java.nio.file.Path

@Parameters(commandDescription = "Start SDK")
class StartCommand : AbstractCommand() {

    internal val sdkSettings: SdkSettingsHolder by sdkKodein.instance()
    private val printWriter: PrintWriter by kodein.instance()
    private val messages by localMessages()

    override fun run() {
        if (sdkSettings.getProperty("repoType") != "local") {
            printWriter.println(messages["start.sdkConfiguredForRemote"])
            return
        }
        if (repositoryStarted()) {
            printWriter.println(messages["start.repositoryAlreadyStarted"])
            return
        }
        startRepository()
        var i = 0
        val padLength = 10
        val msg = messages["start.startingRepository"]
        waitAndPrintProgress(100, msg.padEnd(msg.length + i % padLength, '.'))
        while (!repositoryStarted() && isRepositoryStarting()) {
            if (i % padLength == 0) {
                printWriter.print(msg.padEnd(msg.length + padLength))
            }
            waitAndPrintProgress(100, msg.padEnd(msg.length + i % padLength, '.'))
            i++
        }
        printWriter.println()
        if (!repositoryStarted()) {
            printWriter.println(messages["start.repositoryNotStarted"])
        } else {
            printWriter.println(messages["start.repositoryStarted"])
        }
    }

    private fun isRepositoryStarting(): Boolean {
        return isRepositoryStartingWindows()
    }

    private fun isRepositoryStartingWindows(): Boolean {
        val process = Runtime.getRuntime().exec(
            arrayOf(
                "cmd", "/c", "tasklist /FI \"IMAGENAME eq nexus.exe\""
            )
        )
        val result = process.inputStream.bufferedReader().use { it.readText() }
        return result.contains("nexus.exe")
    }

    private fun startRepository() {
        startWindows()
    }

    private fun startWindows() {
        Runtime.getRuntime().exec(
            arrayOf(
                "cmd", "/c", "start", "\"cuba-sdk-nexus\"", "cmd", "/k",
                Path.of(sdkSettings.getProperty("install-path"))
                    .resolve("nexus3")
                    .resolve("bin")
                    .resolve("nexus").toString(),
                "/run"
            )
        )
    }

    private fun repositoryStarted(): Boolean {
        val (_, response, _) = sdkSettings.getProperty("url").httpHead().response()
        return response.statusCode == 200
    }

    private fun waitAndPrintProgress(period: Long, msg: String) {
        Thread.sleep(period)
        printWriter.print(msg)
    }
}