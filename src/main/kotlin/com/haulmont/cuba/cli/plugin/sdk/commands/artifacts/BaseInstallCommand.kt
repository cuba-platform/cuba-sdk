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

import com.haulmont.cuba.cli.commands.AbstractCommand
import com.haulmont.cuba.cli.cubaplugin.di.sdkKodein
import com.haulmont.cuba.cli.localMessages
import com.haulmont.cuba.cli.plugin.sdk.dto.Component
import com.haulmont.cuba.cli.plugin.sdk.services.ComponentManager
import com.haulmont.cuba.cli.prompting.ValidationException
import org.kodein.di.generic.instance
import java.io.PrintWriter

abstract class BaseInstallCommand : AbstractCommand() {
    internal val PROGRESS_LINE_LENGHT = 100

    internal val componentManager: ComponentManager by sdkKodein.instance()

    internal val printWriter: PrintWriter by sdkKodein.instance()

    internal val messages by localMessages()

    override fun run() {
        search()?.let {
            resolve(it)
            upload(it)
            register(it)
        }
    }

    abstract fun search(): Component?

    private fun upload(component: Component) {
        componentManager.upload(component) { artifact, uploaded, total ->
            printWriter.print(
                printProgress(
                    messages["dependencyUploadProgress"].format(artifact.mvnCoordinates()),
                    uploaded.toFloat() / total * 100
                )
            )
        }
    }

    private fun resolve(component: Component) {
        printWriter.println("Resolving dependencies...")
        componentManager.resolve(component) { resolvedComponent, resolved, total ->
            printWriter.print(
                printProgress(
                    messages["dependencyResolvingProgress"].format(resolvedComponent),
                    resolved.toFloat() / total * 100
                )
            )
        }
    }

    private fun register(component: Component) {
        componentManager.register(component)
    }

    private fun printProgress(message: String, progress: Float): String {
        val progressStr = messages["progress"].format(progress)
        return message.padEnd(PROGRESS_LINE_LENGHT - progressStr.length) + progressStr;
    }

    fun fail(cause: String): Nothing = throw ValidationException(cause)
}