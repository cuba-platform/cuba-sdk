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
import com.haulmont.cuba.cli.CliPlugin
import com.haulmont.cuba.cli.cubaplugin.di.sdkKodein
import com.haulmont.cuba.cli.event.DestroyPluginEvent
import com.haulmont.cuba.cli.event.InitPluginEvent
import com.haulmont.cuba.cli.plugin.sdk.commands.PrintPropertiesCommand
import com.haulmont.cuba.cli.plugin.sdk.commands.SdkCommand
import com.haulmont.cuba.cli.plugin.sdk.commands.artifacts.*
import com.haulmont.cuba.cli.plugin.sdk.commands.repository.*
import com.haulmont.cuba.cli.plugin.sdk.gradle.GradleConnector
import com.haulmont.cuba.cli.plugin.sdk.services.ComponentVersionManager
import org.kodein.di.generic.instance

class SdkPlugin : CliPlugin {

    private val componentVersionsManager: ComponentVersionManager by sdkKodein.instance()

    override val apiVersion: Int
        get() = 5

    @Subscribe
    fun onInit(event: InitPluginEvent) {
        componentVersionsManager.load {}
        event.commandsRegistry {
            command("sdk", SdkCommand()) {
                command("properties", PrintPropertiesCommand())
                command("setup-nexus", SetupNexusCommand())
                command("init", InitCommand())
                command("start", StartCommand())
                command("stop", StopCommand())
                command("set-license", LicenseCommand())
                command("cleanup", CleanCommand())
                command("check-updates", CheckForMinorUpdatesCommand())

                command("repository", RepositoryCommandGroup()) {
                    command("list", ListRepositoryCommand()) {
                        command("sdk", ListTargetRepositoryCommand())
                        command("source", ListSourceRepositoryCommand())
                        command("search", ListSearchRepositoryCommand())
                    }
                    command("add", AddRepositoryCommand()) {
                        command("sdk", AddTargetRepositoryCommand())
                        command("source", AddSourceRepositoryCommand())
                        command("search", AddSearchRepositoryCommand())
                    }
                    command("remove", RemoveRepositoryCommand()) {
                        command("sdk", RemoveTargetRepositoryCommand())
                        command("source", RemoveSourceRepositoryCommand())
                        command("search", RemoveSearchRepositoryCommand())
                    }
                }

                command("import", ImportCommand())

                command("export", ExportCommand()) {
                    command("framework", ExportFrameworkCommand())
                    command("addon", ExportAddonCommand())
                    command("lib", ExportLibCommand())
                }

                command("resolve", ResolveCommand()) {
                    command("framework", ResolveFrameworkCommand())
                    command("addon", ResolveAddonCommand())
                    command("lib", ResolveLibCommand())
                }

                command("push", PushCommand()) {
                    command("framework", PushFrameworkCommand())
                    command("addon", PushAddonCommand())
                    command("lib", PushLibCommand())
                }

                command("install", InstallCommand()) {
                    command("framework", InstallFrameworkCommand())
                    command("addon", InstallAddonCommand())
                    command("lib", InstallLibCommand())
                }

                command("remove", RemoveCommandGroup()) {
                    command("framework", RemoveFrameworkCommand())
                    command("addon", RemoveAddonCommand())
                    command("lib", RemoveLibCommand())
                }

                command("list", ListCommandGroup()) {
                    command("framework", ListFrameworkCommand())
                    command("addon", ListAddonCommand())
                    command("lib", ListLibsCommand())
                }
            }
        }
    }

    @Subscribe
    fun onDestroy(event: DestroyPluginEvent) {
        StopCommand().apply { checkState = false }.execute()
        GradleConnector().runTask("--stop")
    }

}