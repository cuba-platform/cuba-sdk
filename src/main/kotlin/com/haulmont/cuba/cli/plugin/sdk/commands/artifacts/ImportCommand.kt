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
import com.haulmont.cuba.cli.plugin.sdk.dto.Component
import com.haulmont.cuba.cli.plugin.sdk.dto.Repository
import com.haulmont.cuba.cli.plugin.sdk.dto.RepositoryTarget
import com.haulmont.cuba.cli.plugin.sdk.services.ComponentManager
import com.haulmont.cuba.cli.plugin.sdk.services.ImportExportService
import com.haulmont.cuba.cli.plugin.sdk.services.RepositoryManager
import com.haulmont.cuba.cli.plugin.sdk.utils.doubleUnderline
import com.haulmont.cuba.cli.red
import org.kodein.di.generic.instance
import java.nio.file.Files
import java.nio.file.Path

@Parameters(commandDescription = "Import SDK")
class ImportCommand : AbstractSdkCommand() {

    internal val exportService: ImportExportService by sdkKodein.instance()
    internal val componentManager: ComponentManager by sdkKodein.instance()
    internal val repositoryManager: RepositoryManager by sdkKodein.instance()
    internal val workingDirectoryManager: WorkingDirectoryManager by kodein.instance()

    @Parameter(description = "SDK archive to import")
    private var importFile: String? = null

    @Parameter(
        names = ["--repo"],
        description = "Repository",
        hidden = true
    )
    private var repositoryName: String? = null

    @Parameter(names = ["--no-upload"], description = "Do not upload components to repositories", hidden = true)
    var noUpload: Boolean = false
        private set

    override fun run() {
        val importFilePath = Path.of(importFile)
        if (!Files.exists(importFilePath)) {
            printWriter.println(messages["import.fileNotFound"].format(importFile).red())
            return
        }

        var repository: Repository? = null
        if (repositoryName != null) {
            repository = repositoryManager.getRepository(repositoryName!!, RepositoryTarget.TARGET)
            if (repository == null) {
                printWriter.println(messages["repository.unknown"].format(repositoryName).red())
                return
            }
        }

        import(importFilePath)
            .also { upload(it, repository) }
            .also { components ->
                printWriter.println(messages["import.components"].doubleUnderline())
                components.sortedBy { "${it.type}_${it}" }.forEach {
                    printWriter.println("${messages[it.type.toString().toLowerCase()]} $it")
                }
            }
    }

    private fun import(importFilePath: Path): Collection<Component> {
        val components = exportService.import(importFilePath, !noUpload) { count, total ->
            printProgress(
                messages["unzipProgress"],
                calculateProgress(count, total)
            )
        }
        return components
    }

    private fun upload(components: Collection<Component>, repository: Repository?) {
        if (!noUpload && components.isNotEmpty()) {
            printProgress(messages["upload.progress"].format(""), 0f)
            var totalUploaded = 0
            val total = components.flatMap { it.collectAllDependencies() }.count()
            components.forEach { component ->
                componentManager.upload(
                    component,
                    repository
                ) { artifact, _, _ ->
                    printProgress(
                        messages["upload.progress"].format(artifact.mvnCoordinates()),
                        calculateProgress(++totalUploaded, total)
                    )
                }
            }
            printWriter.println()
        }
    }
}