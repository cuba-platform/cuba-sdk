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
import com.haulmont.cuba.cli.event.DestroyPluginEvent
import com.haulmont.cuba.cli.event.InitPluginEvent
import com.haulmont.cuba.cli.plugin.sdk.commands.PrintPropertiesCommand
import com.haulmont.cuba.cli.plugin.sdk.commands.SdkCommand
import com.haulmont.cuba.cli.plugin.sdk.commands.artifacts.*
import com.haulmont.cuba.cli.plugin.sdk.commands.repository.*
import com.haulmont.cuba.cli.plugin.sdk.di.sdkKodein
import com.haulmont.cuba.cli.plugin.sdk.templates.ComponentRegistry
import com.haulmont.cuba.cli.plugin.sdk.templates.CubaAddonProvider
import com.haulmont.cuba.cli.plugin.sdk.templates.CubaFrameworkProvider
import com.haulmont.cuba.cli.plugin.sdk.templates.LibProvider
import org.gradle.tooling.internal.consumer.ConnectorServices
import org.kodein.di.generic.instance
import kotlin.concurrent.thread

class SdkPlugin : CliPlugin {

    private val componentRegistry: ComponentRegistry by sdkKodein.instance<ComponentRegistry>()

    init {
        componentRegistry.addProviders(
            CubaAddonProvider(), CubaFrameworkProvider(), LibProvider()
        //    , SpringBootProvider()
        )
    }

    override val apiVersion: Int
        get() = 5

    @Subscribe
    fun onInit(event: InitPluginEvent) {
        for (provider in componentRegistry.providers()) {
            thread {
                provider.load()
            }
        }
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
                        command("target", ListTargetRepositoryCommand())
                        command("source", ListSourceRepositoryCommand())
                        command("search", ListSearchRepositoryCommand())
                    }
                    command("add", AddRepositoryCommand()) {
                        command("target", AddTargetRepositoryCommand())
                        command("source", AddSourceRepositoryCommand())
                        command("search", AddSearchRepositoryCommand())
                    }
                    command("remove", RemoveRepositoryCommand()) {
                        command("target", RemoveTargetRepositoryCommand())
                        command("source", RemoveSourceRepositoryCommand())
                        command("search", RemoveSearchRepositoryCommand())
                    }
                }

                command("import", ImportCommand())

                command("export", ExportCommand()) {
                    for (provider in componentRegistry.providers()) {
                        command(provider.getType(), ExportComponentCommand(provider))
                    }
                }

                command("resolve", ResolveCommand()) {
                    for (provider in componentRegistry.providers()) {
                        command(provider.getType(), ResolveComponentCommand(provider))
                    }
                }

                command("push", PushCommand()) {
                    for (provider in componentRegistry.providers()) {
                        command(provider.getType(), PushComponentCommand(provider))
                    }
                }

                command("install", InstallCommand()) {
                    for (provider in componentRegistry.providers()) {
                        command(provider.getType(), InstallComponentCommand(provider))
                    }
                }

                command("remove", RemoveCommandGroup()) {
                    for (provider in componentRegistry.providers()) {
                        command(provider.getType(), RemoveComponentCommand(provider))
                    }
                }

                command("list", ListCommandGroup()) {
                    for (provider in componentRegistry.providers()) {
                        command(provider.getType(), ListComponentCommand(provider))
                    }
                }
            }
        }

        Runtime.getRuntime().addShutdownHook(object : Thread() {
            override fun run() {
                StopCommand().apply { checkState = false }.execute()
                ConnectorServices.reset()
            }
        })
    }

    @Subscribe
    fun onDestroy(event: DestroyPluginEvent) {
        StopCommand().apply { checkState = false }.execute()
        ConnectorServices.reset()
    }

}