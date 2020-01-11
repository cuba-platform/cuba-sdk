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

package com.haulmont.cuba.cli.plugin.sdk.commands.artifacts

import com.haulmont.cuba.cli.cubaplugin.di.sdkKodein
import com.haulmont.cuba.cli.localMessages
import com.haulmont.cuba.cli.plugin.sdk.commands.AbstractSdkCommand
import com.haulmont.cuba.cli.plugin.sdk.dto.ComponentType
import com.haulmont.cuba.cli.plugin.sdk.services.MetadataHolder
import com.haulmont.cuba.cli.plugin.sdk.utils.doubleUnderline
import org.kodein.di.generic.instance
import java.io.PrintWriter

abstract class AbstractListCommand : AbstractSdkCommand() {

    internal val messages by localMessages()
    internal val metadataHolder: MetadataHolder by sdkKodein.instance()
    internal val printWriter: PrintWriter by sdkKodein.instance()

    override fun run() {
        printWriter.println(messages["list.resolved.${getComponentType()}"].doubleUnderline())
        for (component in metadataHolder.getMetadata().components.filter { it.type == getComponentType() }) {
            printWriter.println("$component")
        }
        printWriter.println()
        printWriter.println(messages["list.installed.${getComponentType()}"].doubleUnderline())
        for (component in metadataHolder.getMetadata().installedComponents.filter { it.type == getComponentType() }) {
            printWriter.println("$component")
        }
        printWriter.println()
    }

    abstract fun getComponentType(): ComponentType
}