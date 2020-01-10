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
import com.haulmont.cuba.cli.green
import com.haulmont.cuba.cli.red

@Parameters(commandDescription = "Start SDK")
class StartCommand : NexusCommand() {

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
        val msg = messages["start.startingRepository"]
        printProgressMessage(msg)
        while (!repositoryStarted() && isRepositoryStarting()) {
            printProgressMessage(msg, i++)
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
}