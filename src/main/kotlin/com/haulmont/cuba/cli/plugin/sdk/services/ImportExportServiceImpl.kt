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

package com.haulmont.cuba.cli.plugin.sdk.services

import com.google.gson.Gson
import com.haulmont.cuba.cli.cubaplugin.di.sdkKodein
import com.haulmont.cuba.cli.plugin.sdk.dto.Component
import com.haulmont.cuba.cli.plugin.sdk.dto.MvnArtifact
import com.haulmont.cuba.cli.plugin.sdk.dto.SdkMetadata
import org.kodein.di.generic.instance
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.logging.Logger
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ImportExportServiceImpl : ImportExportService {

    private val log: Logger = Logger.getLogger(MavenExecutorImpl::class.java.name)
    private val sdkSettings: SdkSettingsHolder by sdkKodein.instance()

    override fun export(fileName: String, components: Collection<Component>, progress: ExportProcessCallback?): Path {
        val exportDir = Path.of(sdkSettings["sdk.export"]).also {
            if (!Files.exists(it)) Files.createDirectories(it)
        }
        val metadata = SdkMetadata()
        val allDependencies = mutableListOf<MvnArtifact>()
        val paths = mutableSetOf<Path>()
        for (component in components) {
            metadata.components.add(component)
            allDependencies.addAll(collectAllDependencies(component))
        }
        val total = allDependencies.size
        var exported = 0f
        val sdkFileName = exportDir.resolve(fileName)
        ZipOutputStream(BufferedOutputStream(FileOutputStream(sdkFileName.toFile()))).use { out ->
            val data = ByteArray(1024)
            val entry = ZipEntry("export.metadata")
            out.putNextEntry(entry)
            out.write(Gson().toJson(metadata).toByteArray())
            for (artifact in allDependencies) {
                val zipPath = artifact.localPath(Path.of("m2")).parent
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
                progress?.let {
                    progress(artifact, exported, total)
                }
            }
        }
        return sdkFileName
    }

    internal fun collectAllDependencies(component: Component): List<MvnArtifact> {
        val list = mutableListOf<MvnArtifact>()
        list.addAll(component.dependencies)
        for (child in component.components) {
            list.addAll(child.dependencies)
        }
        return list
    }
}