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

package com.haulmont.cuba.cli.plugin.sdk.commands.artifacts

import com.beust.jcommander.Parameters
import com.haulmont.cuba.cli.plugin.sdk.commands.AbstractSdkCommand
import com.haulmont.cuba.cli.plugin.sdk.di.sdkKodein
import com.haulmont.cuba.cli.plugin.sdk.services.MetadataHolder
import com.haulmont.cuba.cli.plugin.sdk.templates.ComponentProvider
import com.haulmont.cuba.cli.plugin.sdk.utils.doubleUnderline
import org.kodein.di.generic.instance

@Parameters(commandDescription = "List available components in SDK")
class ListComponentCommand(val provider: ComponentProvider) : AbstractSdkCommand() {

    internal val metadataHolder: MetadataHolder by sdkKodein.instance<MetadataHolder>()

    override fun run() {
        printWriter.println(messages["list.resolved"].format(provider.getName()).doubleUnderline())
        for (component in metadataHolder.getResolved().filter { it.type == provider.getType() }) {
            printWriter.println("$component")
        }
        printWriter.println()
        printWriter.println(messages["list.installed"].format(provider.getName()).doubleUnderline())
        for (component in metadataHolder.getInstalled().filter { it.type == provider.getType() }) {
            printWriter.println("$component")
        }
        printWriter.println()
    }
}