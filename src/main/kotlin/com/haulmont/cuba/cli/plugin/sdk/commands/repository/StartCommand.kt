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
import com.haulmont.cuba.cli.green
import com.haulmont.cuba.cli.localMessages
import com.haulmont.cuba.cli.plugin.sdk.nexus.NexusManager
import com.haulmont.cuba.cli.plugin.sdk.services.SdkSettingsHolder
import com.haulmont.cuba.cli.red
import org.kodein.di.generic.instance
import java.io.PrintWriter

@Parameters(commandDescription = "Start SDK")
class StartCommand : AbstractCommand() {

    internal val sdkSettings: SdkSettingsHolder by sdkKodein.instance()
    private val printWriter: PrintWriter by kodein.instance()
    private val messages by localMessages()
    private val nexusManager: NexusManager by sdkKodein.instance()

    override fun run() {
        if (sdkSettings["repository.type"] != "local") {
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
            printWriter.println(messages["start.repositoryNotStarted"].red())
        } else {
            printWriter.println(messages["start.repositoryStarted"].format(sdkSettings["repository.url"]).green())
        }
    }

    private fun isRepositoryStarting(): Boolean = nexusManager.isStarted()

    private fun startRepository() {
        nexusManager.startRepository()
    }

    private fun repositoryStarted(): Boolean {
        val (_, response, _) = sdkSettings["repository.url"]
            .httpHead()
            .response()
        return response.statusCode == 200
    }

    private fun waitAndPrintProgress(period: Long, msg: String) {
        Thread.sleep(period)
        printWriter.print(msg)
    }
}