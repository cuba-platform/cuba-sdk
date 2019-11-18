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

package com.haulmont.cuba.cli.plugin.sdk

import com.google.common.eventbus.Subscribe
import com.haulmont.cuba.cli.Cli
import com.haulmont.cuba.cli.CliPlugin
import com.haulmont.cuba.cli.event.InitPluginEvent
import com.haulmont.cuba.cli.plugin.sdk.commands.SdkCommand
import com.haulmont.cuba.cli.plugin.sdk.commands.artifacts.*
import com.haulmont.cuba.cli.plugin.sdk.commands.repository.*
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*

class SdkPlugin : CliPlugin {

    companion object {
        val SDK_PATH = Paths.get(System.getProperty("user.home"), ".haulmont", "cli", "sdk").also {
            if (!Files.exists(it)) {
                Files.createDirectories(it)
            }
        }
    }

    override val apiVersion: Int
        get() = 5

    @Subscribe
    fun onInit(event: InitPluginEvent) {
        event.commandsRegistry {
            command("sdk", SdkCommand()) {
                command("setup", SetupCommand())
                command("start", StartCommand())
                command("stop", StopCommand())
                command("add-repository", AddRepositoryCommand())
                command("set-license", LicenseCommand())
                command("clean", CleanCommand())
                command("docker", DockerCommand())

                command("import", ImportCommand())
                command("export", ExportCommand())

                command("install", InstallCommand()) {
                    command("framework", InstallFrameworkCommand())
                    command("addon", InstallAddonCommand())
                    command("lib", InstallLibCommand())
                }

                command("remove", RemoveCommand()) {
                    command("framework", RemoveFrameworkCommand())
                    command("addon", RemoveAddonCommand())
                    command("lib", RemoveLibCommand())
                }

                command("list", ListCommand()) {
                    command("framework", ListFrameworkCommand())
                    command("addon", ListAddonCommand())
                    command("lib", ListLibsCommand())
                }
            }
        }
    }


}