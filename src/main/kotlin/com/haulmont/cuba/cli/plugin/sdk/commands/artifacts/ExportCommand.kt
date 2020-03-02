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

import com.beust.jcommander.Parameters
import com.haulmont.cuba.cli.plugin.sdk.dto.Component
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Parameters(commandDescription = "Export all dependencies")
class ExportCommand : AbstractExportCommand() {

    override fun componentsToExport(): Collection<Component> {
        return metadataHolder.getResolved()
    }

    override fun exportName() = "sdk-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH:mm"))

    override fun createSearchContext(): Component? {
        return null
    }
}