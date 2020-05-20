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
import com.haulmont.cuba.cli.plugin.sdk.di.sdkKodein
import com.haulmont.cuba.cli.plugin.sdk.dto.Component
import com.haulmont.cuba.cli.plugin.sdk.dto.RepositoryTarget
import com.haulmont.cuba.cli.plugin.sdk.dto.SdkMetadata
import com.haulmont.cuba.cli.plugin.sdk.utils.UnzipProcessCallback
import org.kodein.di.generic.instance
import java.io.*
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.logging.Logger
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class ImportExportServiceImpl : ImportExportService {

    private val log: Logger = Logger.getLogger(MavenExecutorImpl::class.java.name)
    private val sdkSettings: SdkSettingsHolder by sdkKodein.instance<SdkSettingsHolder>()
    private val componentManager: ComponentManager by sdkKodein.instance<ComponentManager>()
    private val artifactManager: ArtifactManager by sdkKodein.instance<ArtifactManager>()
    private val repositoryManager: RepositoryManager by sdkKodein.instance<RepositoryManager>()

    override fun export(fileName: String, components: Collection<Component>, progress: ExportProcessCallback?): Path {
        val exportDir = Path.of(sdkSettings["sdk.export.path"]).also {
            if (!Files.exists(it)) Files.createDirectories(it)
        }
        val metadata = SdkMetadata().apply {
            this.components.addAll(components)
        }
        val allDependencies = components.flatMap { it.collectAllDependencies() }.toList()
        val paths = mutableSetOf<Path>()
        val total = allDependencies.size
        var exported = 0
        val sdkFileName = exportDir.resolve(fileName)
        ZipOutputStream(BufferedOutputStream(FileOutputStream(sdkFileName.toFile()))).use { out ->
            val data = ByteArray(1024)
            val metadataEntry = ZipEntry("sdk.metadata")
            out.putNextEntry(metadataEntry)
            out.write(Gson().toJson(metadata).toByteArray())
            for (artifact in allDependencies) {
                val zipPath = artifact.localPath(Path.of("m2")).parent
                for (classifier in artifact.classifiers) {
                    artifactManager.getOrDownloadArtifactFile(artifact, classifier).let {
                        val file = it.toFile()
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
                }
                progress?.let {
                    it(artifact, ++exported, total)
                }
            }
        }
        return sdkFileName
    }

    override fun import(
        importFilePath: Path,
        uploadRequired: Boolean,
        unzipProgressFun: UnzipProcessCallback?
    ): Collection<Component> {
        val localRepository = repositoryManager.getRepository("sdk-local", RepositoryTarget.SOURCE)
            ?: throw IllegalStateException("\"sdk-local\" source repository does not exists")
        val targetDir = Path.of(localRepository.url).also {
            if (!Files.exists(it)) {
                Files.createDirectories(it)
            }
        }
        var sdkMetadata: SdkMetadata? = null
        ZipFile(importFilePath.toFile()).use { zip ->
            val total = zip.entries().asSequence().count()
            var count = 0
            zip.entries().asSequence().forEach { entry ->
                zip.getInputStream(entry).use { input ->
                    var entryName = entry.name
                    if (entryName.startsWith("m2")) {
                        entryName = entryName.replaceFirst("m2\\", "")
                    }
                    targetDir.resolve(entryName).also {
                        Files.createDirectories(it.parent)
                        if (Files.exists(it)) {
                            Files.delete(it)
                        }
                    }.also { zipPath ->
                        if (!entry.isDirectory) {
                            if (entryName == "sdk.metadata") {
                                sdkMetadata = readMetadata(input)
                            } else {
                                Files.createFile(zipPath)
                                    .toFile().outputStream().use { output ->
                                        input.copyTo(output)
                                    }
                            }
                        }
                    }
                }

                unzipProgressFun?.let { it(++count, total) }
            }
        }
        val components: Collection<Component> = sdkMetadata?.components ?: emptySet()
        components.forEach { componentManager.register(it) }
        return components
    }

    private fun readMetadata(input: InputStream): SdkMetadata =
        input.bufferedReader(StandardCharsets.UTF_8).use {
            return Gson().fromJson(it.readText(), SdkMetadata::class.java)
        }


}