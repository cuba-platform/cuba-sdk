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
import com.haulmont.cli.core.red
import com.haulmont.cuba.cli.plugin.sdk.di.sdkKodein
import com.haulmont.cuba.cli.plugin.sdk.dto.Component
import com.haulmont.cuba.cli.plugin.sdk.dto.Repository
import com.haulmont.cuba.cli.plugin.sdk.dto.RepositoryTarget
import com.haulmont.cuba.cli.plugin.sdk.services.ImportExportService
import com.haulmont.cuba.cli.plugin.sdk.utils.doubleUnderline
import org.kodein.di.generic.instance
import java.nio.file.Files
import java.nio.file.Path

@Parameters(commandDescription = "Import SDK")
class ImportCommand : BaseComponentCommand() {

    internal val exportService: ImportExportService by sdkKodein.instance<ImportExportService>()

    @Parameter(description = "SDK archive to import")
    private var importFile: String? = null

    @Parameter(
        names = ["--r", "--repository"],
        description = "Repository",
        hidden = true,
        variableArity = true
    )
    private var repositoryNames: List<String>? = null

    @Parameter(names = ["--no-upload"], description = "Do not upload components to repositories", hidden = true)
    var noUpload: Boolean = false
        private set

    override fun createSearchContext(): Component? = null

    override fun run() {
        if (importFile == null) {
            printWriter.println(messages["import.emptyPath"].red())
            return
        }

        var importFilePath = Path.of(importFile)
        if (!importFilePath.isAbsolute) {
            importFilePath = workingDirectoryManager.workingDirectory.resolve(importFilePath)
        }
        if (!Files.exists(importFilePath)) {
            printWriter.println(messages["import.fileNotFound"].format(importFile).red())
            return
        }

        val repositories: List<Repository>? =
            repositories(repositoryNames ?: repositoryManager.getRepositories(RepositoryTarget.TARGET).map { it.name })

        if (repositories == null) {
            printWriter.println(messages["repository.noTargetRepositories"].red())
            return
        }

        import(importFilePath)
            .also { upload(it, repositories) }
            .also { components ->
                printWriter.println(messages["import.components"].doubleUnderline())
                components.sortedBy { "${it.type}_${it}" }.forEach {
                    printWriter.println("${messages[it.type.toLowerCase()]} $it")
                }
            }
        printWriter.println()
    }

    private fun import(importFilePath: Path): Collection<Component> =
        exportService.import(importFilePath, !noUpload) { count, total ->
            printProgress(
                rootMessages["unzipProgress"],
                calculateProgress(count, total)
            )
        }

    private fun upload(components: Collection<Component>, repositories: List<Repository>) {
        if (!noUpload && components.isNotEmpty()) {
            printProgress(messages["upload.progress"].format(""), 0f)
            var totalUploaded = 0
            val total = components.flatMap { it.collectAllDependencies() }.count()
            components.forEach { component ->
                componentManager.upload(
                    component,
                    repositories
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