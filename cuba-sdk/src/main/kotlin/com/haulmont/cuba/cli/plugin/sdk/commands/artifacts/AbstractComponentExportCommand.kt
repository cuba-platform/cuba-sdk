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

import com.beust.jcommander.Parameter
import com.haulmont.cli.core.green
import com.haulmont.cli.core.prompting.Prompts
import com.haulmont.cli.core.red
import com.haulmont.cuba.cli.plugin.sdk.dto.Component

abstract class AbstractComponentExportCommand : AbstractExportCommand() {

    @Parameter(
        description = "Component name and version <name>:<version> or in full coordinates format <group>:<name>:<version>",
        hidden = true
    )
    internal var nameVersion: String? = null

    var componentToExport: Component? = null

    override fun componentsToExport(): List<Component>? =
        componentToExport(componentToExport ?: createSearchContext())?.let { componentWithDependents(it) }

    fun componentToExport(searchContext: Component?): Component? {
        componentToExport?.let { return it }
        searchContext?.let {
            componentToExport = searchInMetadata(it)
        }
        return componentToExport
    }

    override fun exportName(): String {
        (componentToExport ?: createSearchContext())?.let {
            val component = searchInMetadata(it)
            component?.let {
                return "${it.toString().replace(":", "-")}_${it.type}_sdk".toLowerCase()
            }
        }
        return "export"
    }

    override fun run() {
        val searchContext = createSearchContext()
        if (searchContext == null) {
            printWriter.println(messages["export.unknownComponent"].format(nameVersion).red())
            return
        }
        if (componentToExport(searchContext) == null) {
            val answers = Prompts.create {
                confirmation("need-to-resolve", messages["export.needToResolve"].format(searchContext)) {
                    default(true)
                }
            }.ask()
            if (answers["need-to-resolve"] as Boolean) {
                searchContext.let {
                    val component = search(it)
                    component?.let {
                        resolve(componentWithDependents(component))
                        printWriter.println()
                        printWriter.println(messages["resolved"].green())
                        componentToExport(searchContext)
                        super.run()
                    } ?: printWriter.println(messages["export.unknownComponent"].format(it).red())
                }
            }
        } else {
            super.run()
        }
    }
}