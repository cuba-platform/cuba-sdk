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
import com.haulmont.cli.core.green
import com.haulmont.cli.core.red
import com.haulmont.cuba.cli.plugin.sdk.di.sdkKodein
import com.haulmont.cuba.cli.plugin.sdk.dto.Component
import com.haulmont.cuba.cli.plugin.sdk.services.ImportExportService
import com.haulmont.cuba.cli.plugin.sdk.utils.formatPath
import org.kodein.di.generic.instance

@Parameters(commandDescription = "Export SDK")
abstract class AbstractExportCommand : BaseComponentCommand() {

    internal val exportService: ImportExportService by sdkKodein.instance<ImportExportService>()

    override fun run() {
        val components = componentsToExport()
        if (components == null || components.isEmpty()) {
            printWriter.println(messages["export.nothingToExport"].red())
            return
        }
        val sdkArchive = exportService.export("${exportName()}.zip", components) { artifact, exported, total ->
            printProgress(
                messages["export.progress"].format(artifact.mvnCoordinates()),
                calculateProgress(exported, total)
            )
        }
        printWriter.println(messages["export.exportedTo"].format(sdkArchive.toString().formatPath()).green())
    }

    abstract fun componentsToExport(): Collection<Component>?

    abstract fun exportName(): String
}