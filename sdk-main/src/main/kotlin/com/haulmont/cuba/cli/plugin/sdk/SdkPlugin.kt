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
import com.haulmont.cli.core.*
import com.haulmont.cli.core.event.DestroyPluginEvent
import com.haulmont.cli.core.event.InitPluginEvent
import com.haulmont.cuba.cli.plugin.sdk.commands.PrintPropertiesCommand
import com.haulmont.cuba.cli.plugin.sdk.commands.SdkCommand
import com.haulmont.cuba.cli.plugin.sdk.commands.artifacts.*
import com.haulmont.cuba.cli.plugin.sdk.commands.repository.*
import com.haulmont.cuba.cli.plugin.sdk.di.sdkKodein
import com.haulmont.cuba.cli.plugin.sdk.perf.SdkPerformance.sdkSettings
import com.haulmont.cuba.cli.plugin.sdk.templates.ComponentRegistry
import com.haulmont.cuba.cli.plugin.sdk.templates.LibProvider
import org.jline.terminal.Terminal
import org.kodein.di.generic.instance
import java.io.PrintWriter
import java.nio.file.Path
import kotlin.concurrent.thread

class SdkPlugin : MainCliPlugin {

    override val pluginsDir: Path? =
        java.nio.file.Paths.get(System.getProperty("user.home"), ".haulmont", "sdk", "plugins")
    override val priority: Int = 900
    override val prompt: String = "sdk>"

    private val writer: PrintWriter by kodein.instance<PrintWriter>()

    private val messages by localMessages()

    private val terminal: Terminal by kodein.instance<Terminal>()

    private val componentRegistry: ComponentRegistry by sdkKodein.instance<ComponentRegistry>()

    override val apiVersion = API_VERSION

    override fun welcome() {
        writer.println(messages["welcomeMessage"].trimMargin())
    }

    @Subscribe
    fun onInit(event: InitPluginEvent) {

        componentRegistry.addProviders(LibProvider())

        event.commandsRegistry {
            command("sdk", SdkCommand())
            command("properties", PrintPropertiesCommand())
            command("setup-nexus", SetupNexusCommand())
            command("sdk-home", SdkHomeCommand())
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
                }
                command("add", AddRepositoryCommand()) {
                    command("target", AddTargetRepositoryCommand())
                    command("source", AddSourceRepositoryCommand())
                }
                command("remove", RemoveRepositoryCommand()) {
                    command("target", RemoveTargetRepositoryCommand())
                    command("source", RemoveSourceRepositoryCommand())
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

        if (!sdkSettings.sdkConfigured()){
            writer.println(messages["setup.initSdk"].green())
            InitCommand().execute()
        }

        writer.println(messages["sdk.currentHome"].format(sdkSettings.sdkHome()).green())

        Runtime.getRuntime().addShutdownHook(thread (isDaemon = true){
                StopCommand().apply { checkState = false }.execute()
        })
    }

    @Subscribe
    fun onDestroy(event: DestroyPluginEvent) {
        StopCommand().apply { checkState = false }.execute()
    }

}