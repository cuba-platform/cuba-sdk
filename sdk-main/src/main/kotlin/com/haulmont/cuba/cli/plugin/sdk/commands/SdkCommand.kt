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
import com.haulmont.cli.core.green
import com.haulmont.cli.core.red
import com.haulmont.cuba.cli.plugin.sdk.di.sdkKodein
import com.haulmont.cuba.cli.plugin.sdk.nexus.NexusManager
import com.haulmont.cuba.cli.plugin.sdk.services.ArtifactManager
import com.haulmont.cuba.cli.plugin.sdk.utils.doubleUnderline
import org.kodein.di.generic.instance

@Parameters(commandDescription = "CUBA SDK")
class SdkCommand : AbstractSdkCommand() {

    private val nexusManager: NexusManager by sdkKodein.instance<NexusManager>()
    private val artifactManager: ArtifactManager by lazy { ArtifactManager.instance()}

    override fun run() {
        if (!sdkSettings.sdkConfigured()){
            printWriter.println(messages["sdk.notConfigured"])
            return
        }
        printWriter.println(messages["sdk.title"].doubleUnderline())
        printWriter.println("SDK home: ${sdkSettings["sdk.home"].green()}")
        if (nexusManager.isLocal()) {
            printWriter.print("Repository type: ${"local".green()} ")
            printWriter.print(if (nexusManager.isStarted()) "running".green() else "stopped".red())
            printWriter.println()
            printWriter.println("Repository URL: ${sdkSettings["repository.url"].green()}")
            printWriter.println("Repository install path: ${sdkSettings["repository.path"].green()}")
        }
        artifactManager.printInfo()
    }

    override fun onlyForConfiguredSdk() = false
}