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

import com.github.kittinunf.fuel.httpHead
import com.haulmont.cuba.cli.commands.AbstractCommand
import com.haulmont.cuba.cli.cubaplugin.di.sdkKodein
import com.haulmont.cuba.cli.localMessages
import com.haulmont.cuba.cli.plugin.sdk.nexus.NexusManager
import com.haulmont.cuba.cli.plugin.sdk.services.SdkSettingsHolder
import org.kodein.di.generic.instance
import java.io.PrintWriter

abstract class NexusCommand : AbstractCommand() {

    internal val sdkSettings: SdkSettingsHolder by sdkKodein.instance()
    internal val printWriter: PrintWriter by kodein.instance()
    internal val messages by localMessages()
    internal val nexusManager: NexusManager by sdkKodein.instance()

    internal fun repositoryStarted(): Boolean {
        val (_, response, _) = sdkSettings["repository.url"]
            .httpHead()
            .response()
        return response.statusCode == 200
    }

    internal fun printProgressMessage(msg: String, i: Int = 0) {
        val padLength = 10
        if (i % padLength == 0) {
            printWriter.print(msg.padEnd(msg.length + padLength))
        }
        waitAndPrintProgress(100, msg.padEnd(msg.length + i % padLength, '.'))
    }

    internal fun waitAndPrintProgress(period: Long, msg: String) {
        Thread.sleep(period)
        printWriter.print(msg)
    }

    internal fun waitTask(msg: String, waitConditionFun: () -> Boolean) {
        printProgressMessage(msg)
        var i = 0
        while (waitConditionFun()) {
            printProgressMessage(msg, i++)
        }
        printWriter.println()
    }
}