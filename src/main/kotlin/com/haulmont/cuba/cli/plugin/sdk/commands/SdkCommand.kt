/*
 * Copyright (c) 2008-2018 Haulmont.
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

package com.haulmont.cuba.cli.plugin.sdk.commands

import com.beust.jcommander.Parameters
import com.haulmont.cuba.cli.commands.AbstractCommand
import com.haulmont.cuba.cli.cubaplugin.di.sdkKodein
import com.haulmont.cuba.cli.green
import com.haulmont.cuba.cli.localMessages
import com.haulmont.cuba.cli.plugin.sdk.nexus.NexusManager
import com.haulmont.cuba.cli.plugin.sdk.services.SdkSettingsHolder
import com.haulmont.cuba.cli.plugin.sdk.utils.doubleUnderline
import com.haulmont.cuba.cli.red
import org.kodein.di.generic.instance
import java.io.PrintWriter

@Parameters(commandDescription = "CUBA SDK")
class SdkCommand : AbstractCommand() {

    internal val sdkSettings: SdkSettingsHolder by sdkKodein.instance()
    private val printWriter: PrintWriter by kodein.instance()
    private val messages by localMessages()
    private val nexusManager: NexusManager by sdkKodein.instance()

    override fun run() {
        printWriter.println(messages["sdk.title"].doubleUnderline())
        printWriter.println("SDK home: ${sdkSettings["sdk.home"].green()}")
        printWriter.print("Repository type: ${sdkSettings["repository.type"].green()} ")
        if (isLocalRepository()) {
            printWriter.print(if (nexusManager.isStarted()) "running".green() else "stopped".red())
            printWriter.println()
            printWriter.println("Repository URL: ${sdkSettings["repository.url"].green()}")
            printWriter.println("Repository install path: ${sdkSettings["repository.path"].green()}")
        } else {
            printWriter.println()
        }
        printWriter.println("Maven install path: ${sdkSettings["mvn.path"].green()}")
        printWriter.println("Maven local repository: ${sdkSettings["mvn.local.repo"].green()}")

    }

    private fun isLocalRepository() = sdkSettings["repository.type"] == "local"
}