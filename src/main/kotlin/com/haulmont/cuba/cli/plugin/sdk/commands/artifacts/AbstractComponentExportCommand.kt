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

import com.haulmont.cuba.cli.plugin.sdk.dto.Component
import com.haulmont.cuba.cli.red

abstract class AbstractComponentExportCommand : AbstractExportCommand() {

    override fun componentsToExport(): Collection<Component> {
        createSearchContext()?.let {
            val component = searchInMetadata(it)
            if (component == null) {
                printWriter.println(messages["notResolved"].red())
                return emptyList()
            }
            return listOf(component)
        }
        return emptyList()
    }

    override fun exportName(): String {
        createSearchContext()?.let {
            val component = searchInMetadata(it)
            component?.let {
                return "sdk-${it.type.toString().toLowerCase()}_${it.toString().replace(":", "-")}"
            }
        }
        return "export"
    }
}