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
import com.google.gson.Gson
import com.haulmont.cuba.cli.plugin.sdk.dto.Component
import com.haulmont.cuba.cli.plugin.sdk.dto.MvnArtifact
import com.haulmont.cuba.cli.plugin.sdk.dto.SdkMetadata
import com.haulmont.cuba.cli.red
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@Parameters(commandDescription = "Export SDK")
abstract class AbstractExportCommand : BaseComponentCommand() {

    override fun run() {
        val exportDir = Path.of(sdkSettings["sdk.export"]).also {
            if (!Files.exists(it)) Files.createDirectories(it)
        }
        val components = componentsToExport()
        if (components.isEmpty()) {
            printWriter.println(messages["export.nothingToExport"].red())
            return
        }
        val metadata = SdkMetadata()
        val allDependencies = mutableListOf<MvnArtifact>()
        val paths = mutableSetOf<Path>()
        for (component in componentsToExport()) {
            metadata.components.add(component)
            allDependencies.addAll(collectAllDependencies(component))
        }
        val total = allDependencies.size
        var exported = 0f
        ZipOutputStream(BufferedOutputStream(FileOutputStream(exportDir.resolve("${exportName()}.zip").toFile()))).use { out ->
            val data = ByteArray(1024)
            val entry = ZipEntry("export.metadata")
            out.putNextEntry(entry)
            out.write(Gson().toJson(metadata).toByteArray())
            for (artifact in allDependencies) {
                val zipPath = artifact.localPath(Path.of(".m2")).parent
                artifact.localPath(Path.of(sdkSettings["maven.local.repo"]))
                    .parent.toFile().listFiles().forEach { file ->
                    FileInputStream(file).use { fi ->
                        BufferedInputStream(fi).use { origin ->
                            val path = zipPath.resolve(file.name)
                            if (!paths.contains(path)) {
                                val entry = ZipEntry(path.toString())
                                out.putNextEntry(entry)
                                while (true) {
                                    val readBytes = origin.read(data)
                                    if (readBytes == -1) {
                                        break
                                    }
                                    out.write(data, 0, readBytes)
                                }
                                paths.add(path)
                            }
                        }
                    }
                }
                exported++
                printWriter.print(
                    printProgress(
                        messages["dependencyExportProgress"].format(artifact.mvnCoordinates()),
                        exported / total * 100
                    )
                )
            }
        }
        printWriter.println()
    }

    abstract fun componentsToExport(): Collection<Component>

    abstract fun exportName(): String

    internal fun collectAllDependencies(component: Component): List<MvnArtifact> {
        val list = mutableListOf<MvnArtifact>()
        list.addAll(component.dependencies)
        for (child in component.components) {
            list.addAll(child.dependencies)
        }
        return list
    }
}