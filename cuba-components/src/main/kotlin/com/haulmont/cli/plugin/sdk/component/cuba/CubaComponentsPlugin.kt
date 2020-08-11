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

package com.haulmont.cli.plugin.sdk.component.cuba

import com.google.common.eventbus.Subscribe
import com.haulmont.cli.core.API_VERSION
import com.haulmont.cli.core.CliPlugin
import com.haulmont.cli.core.event.InitPluginEvent
import com.haulmont.cli.plugin.sdk.component.cuba.commands.LicenseCommand
import com.haulmont.cli.plugin.sdk.component.cuba.providers.CubaAddonProvider
import com.haulmont.cli.plugin.sdk.component.cuba.providers.CubaFrameworkProvider
import com.haulmont.cuba.cli.plugin.sdk.di.sdkKodein
import com.haulmont.cuba.cli.plugin.sdk.templates.ComponentRegistry
import org.kodein.di.generic.instance
import kotlin.concurrent.thread

class CubaComponentsPlugin : CliPlugin {
    override val apiVersion = API_VERSION

    private val componentRegistry: ComponentRegistry by sdkKodein.instance<ComponentRegistry>()

    @Subscribe
    fun onInit(event: InitPluginEvent) {
        System.setProperty("deployment.security.TLSv1.2", "true");
        System.setProperty("https.protocols", "TLSv1.2");

        componentRegistry.addProviders(
            CubaAddonProvider(), CubaFrameworkProvider()
        )

        componentRegistry.providers().forEach {
            thread {
                it.load()
            }
        }

        event.commandsRegistry {
            command("set-license", LicenseCommand())
        }
    }

}