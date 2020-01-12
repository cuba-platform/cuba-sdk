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
import com.beust.jcommander.Parameters
import com.haulmont.cuba.cli.WorkingDirectoryManager
import com.haulmont.cuba.cli.cubaplugin.di.sdkKodein
import com.haulmont.cuba.cli.plugin.sdk.commands.AbstractSdkCommand
import com.haulmont.cuba.cli.plugin.sdk.services.ImportExportService
import com.haulmont.cuba.cli.plugin.sdk.utils.doubleUnderline
import com.haulmont.cuba.cli.red
import org.kodein.di.generic.instance
import java.nio.file.Files
import java.nio.file.Path

@Parameters(commandDescription = "Import SDK")
class ImportCommand : AbstractSdkCommand() {

    internal val exportService: ImportExportService by sdkKodein.instance()
    internal val workingDirectoryManager: WorkingDirectoryManager by kodein.instance()

    @Parameter(description = "SDK archive to import")
    private var importFile: String? = null

    @Parameter(names = ["--no-upload"], description = "Do not upload components to repositories", hidden = true)
    var noUpload: Boolean = false
        private set

    override fun run() {
        val importFilePath = Path.of(importFile)
        if (!Files.exists(importFilePath)) {
            printWriter.println(messages["import.fileNotFound"].format(importFile).red())
            return
        }
        val components = exportService.import(importFilePath, !noUpload,
            { count, total ->
                printWriter.print(
                    printProgress(
                        messages["unzipProgress"],
                        100 * count.toFloat() / total.toFloat()
                    )
                )
            },
            { artifact, uploaded, total ->
                printWriter.print(
                    printProgress(
                        messages["dependencyUploadProgress"].format(artifact.mvnCoordinates()),
                        uploaded / total * 100
                    )
                )
            }
        )
        printWriter.println()
        printWriter.println(messages["import.components"].doubleUnderline())
        for (component in components.sortedBy { "${it.type}_${it}" }) {
            printWriter.println("${messages[component.type.toString().toLowerCase()]} $component")
        }
    }
}