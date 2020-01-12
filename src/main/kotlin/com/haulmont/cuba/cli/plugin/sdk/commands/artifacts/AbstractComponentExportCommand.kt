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

import com.haulmont.cuba.cli.green
import com.haulmont.cuba.cli.plugin.sdk.dto.Component
import com.haulmont.cuba.cli.prompting.Prompts

abstract class AbstractComponentExportCommand : AbstractExportCommand() {

    var componetToExport: Component? = null

    override fun componentsToExport(): List<Component>? = componentToExport()?.let { listOf(it) }

    fun componentToExport(): Component? {
        componetToExport?.let { return it }
        createSearchContext()?.let {
            return searchInMetadata(it)
        }
        return null
    }

    override fun exportName(): String {
        createSearchContext()?.let {
            val component = searchInMetadata(it)
            component?.let {
                return "${it.toString().replace(":", "-")}_${it.type.toString().toLowerCase()}_sdk"
            }
        }
        return "export"
    }

    override fun run() {
        if (componentsToExport() == null) {
            val searchContext = createSearchContext()
            val answers = Prompts.create {
                confirmation("need-to-resolve", messages["export.needToResolve"].format(searchContext)) {
                    default(true)
                }
            }.ask()
            if (answers["need-to-resolve"] as Boolean) {
                searchContext?.let {
                    val component = search(it)
                    component?.let {
                        resolve(component)
                        register(component)
                    }
                    printWriter.println()
                    printWriter.println(messages["resolved"].green())
                    componentToExport()
                    super.run()
                }
            }
        } else {
            super.run()
        }
    }
}