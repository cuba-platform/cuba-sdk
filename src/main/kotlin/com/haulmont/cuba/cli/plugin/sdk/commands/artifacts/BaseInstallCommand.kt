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

import com.beust.jcommander.Parameter
import com.haulmont.cuba.cli.commands.AbstractCommand
import com.haulmont.cuba.cli.cubaplugin.di.sdkKodein
import com.haulmont.cuba.cli.localMessages
import com.haulmont.cuba.cli.plugin.sdk.dto.Component
import com.haulmont.cuba.cli.plugin.sdk.services.ComponentManager
import com.haulmont.cuba.cli.prompting.ValidationException
import org.kodein.di.generic.instance
import java.io.PrintWriter

abstract class BaseInstallCommand : AbstractCommand() {

    @Parameter(names = ["--force"], description = "Force resolve and upload dependencies", hidden = true)
    var force: Boolean = false
        private set

    internal val PROGRESS_LINE_LENGHT = 110

    internal val componentManager: ComponentManager by sdkKodein.instance()

    internal val printWriter: PrintWriter by sdkKodein.instance()

    internal val messages by localMessages()

    override fun run() {
        createSearchContext()?.let {
            if (force || !componentManager.isAlreadyInstalled(it)) {
                var component = searchInMetadata(it)
                if (force || component == null) {
                    component = search(it)?.also {
                        resolve(it)
                        register(it)
                    }
                }
                component?.let { upload(it) }
                printWriter.println()
                printWriter.println(messages["installed"])
            } else {
                printWriter.println(messages["alreadyInstalled"])
            }
        }
    }

    abstract fun createSearchContext(): Component?

    private fun upload(component: Component) {
        printWriter.println()
        printWriter.println("Uploading dependencies...")
        componentManager.upload(component) { artifact, uploaded, total ->
            printWriter.print(
                printProgress(
                    messages["dependencyUploadProgress"].format(artifact.mvnCoordinates()),
                    uploaded / total * 100
                )
            )
        }
        printWriter.print(
            printProgress(messages["uploaded"], 100f)
        )
    }

    private fun resolve(component: Component) {
        printWriter.println("Resolving dependencies...")
        componentManager.resolve(component) { resolvedComponent, resolved, total ->
            printWriter.print(
                printProgress(
                    messages["dependencyResolvingProgress"].format(resolvedComponent),
                    resolved / total * 100
                )
            )
        }
        printWriter.print(
            printProgress(messages["resolved"], 100f)
        )
    }

    internal fun register(component: Component) {
        componentManager.register(component)
    }

    internal fun searchInMetadata(component: Component): Component? {
        return componentManager.searchInMetadata(component)
    }

    open fun search(component: Component): Component? {
        return componentManager.search(component)
    }

    private fun printProgress(message: String, progress: Float): String {
        val progressStr = messages["progress"].format(progress)
        return "\r" + message.padEnd(PROGRESS_LINE_LENGHT - progressStr.length) + progressStr;
    }

    fun fail(cause: String): Nothing = throw ValidationException(cause)
}