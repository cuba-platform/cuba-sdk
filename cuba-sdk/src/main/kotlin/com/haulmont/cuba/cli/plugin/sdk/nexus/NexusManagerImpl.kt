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

package com.haulmont.cuba.cli.plugin.sdk.nexus

import com.haulmont.cuba.cli.plugin.sdk.di.sdkKodein
import com.haulmont.cuba.cli.plugin.sdk.dto.OsType
import com.haulmont.cuba.cli.plugin.sdk.services.SdkSettingsHolder
import com.haulmont.cuba.cli.plugin.sdk.utils.currentOsType
import org.kodein.di.generic.instance
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.concurrent.thread

class NexusManagerImpl : NexusManager {

    internal val sdkSettings: SdkSettingsHolder by sdkKodein.instance<SdkSettingsHolder>()
    internal var process: Process? = null

    override fun isLocal(): Boolean = sdkSettings.getIfExists("repository.type") == "local"

    override fun startRepository() {
        thread {
            val newProcess = Runtime.getRuntime().exec(
                arrayOf(
                    sdkSettings.nexusRepositoryPath().toString(),
                    if (currentOsType() == OsType.WINDOWS) "/run" else "run"
                )
            )
            InputStreamReader(newProcess.inputStream).use {
                BufferedReader(it).use { }
            }
            process = newProcess
        }
    }

    override fun stopRepository() {
        stopInternal()
    }

    private fun stopInternal() {
        if (process != null) {
            process?.destroy()
        } else {
            ProcessHandle.allProcesses()
                .filter { p ->
                    p.info().commandLine()
                        .map { it.contains("nexus") }
                        .orElse(false)
                }
                .findFirst()
                .ifPresent { it.destroy() }
        }
    }

    override fun isStarted(): Boolean {
        return process?.isAlive ?: false
    }

}