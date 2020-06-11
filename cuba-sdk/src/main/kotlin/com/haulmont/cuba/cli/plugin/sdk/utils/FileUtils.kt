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

package com.haulmont.cuba.cli.plugin.sdk.utils

import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.Request
import com.github.kittinunf.fuel.core.Response
import com.github.kittinunf.result.Result
import org.apache.commons.compress.archivers.ArchiveEntry
import org.apache.commons.compress.archivers.ArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import java.io.*
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream


typealias UnzipProcessCallback = (count: Int, total: Int) -> Unit

fun File.copyInputStreamToFile(inputStream: InputStream) {
    this.outputStream().use { fileOut ->
        inputStream.copyTo(fileOut)
    }
}

class FileUtils {
    companion object {

        @Throws(IOException::class)
        fun deleteDirectory(path: Path?) {
            Files.walk(path)
                .sorted(Comparator.reverseOrder())
                .map { obj: Path -> obj.toFile() }
                .forEach { obj: File -> obj.delete() }
        }

        fun unzip(
            fileName: Path, targetDir: Path, skipFirstEntry: Boolean = false,
            progressFun: UnzipProcessCallback? = null
        ): Path {
            var total = 0
            var count = 0
            createArchiveEntry(fileName).use { zip ->
                while (zip.nextEntry != null) {
                    total++
                }
            }
            var firstEntry: ArchiveEntry? = null
            createArchiveEntry(fileName).use { zip ->
                var entry: ArchiveEntry? = zip.nextEntry

                if (firstEntry == null) {
                    firstEntry = entry
                }

                while (entry != null) { // create a file with the same name as the tarEntry
                    var entryName = entry.name
                    if (skipFirstEntry && firstEntry != null) {
                        entryName = entryName.replaceFirst(firstEntry!!.getName(), "")
                    }
                    val destPath = targetDir.resolve(entryName)
                    if (Files.exists(destPath)) {
                        Files.delete(destPath)
                    }
                    if (entry.isDirectory) {
                        Files.createDirectories(destPath)
                    } else {
                        if (!Files.exists(destPath.parent)) {
                            Files.createDirectories(destPath.parent)
                        }
                        Files.createFile(destPath)
                        var btoRead: ByteArray? = ByteArray(1024)
                        BufferedOutputStream(FileOutputStream(destPath.toFile())).use {
                            var len = 0
                            while (zip.read(btoRead).also { len = it } != -1) {
                                it.write(btoRead, 0, len)
                            }
                        }
                        btoRead = null

                    }
                    progressFun?.let { it(++count, total) }
                    entry = zip.nextEntry
                }
            }
            return targetDir
        }

        private fun createArchiveEntry(zipFileName: Path): ArchiveInputStream {
            val ext = zipFileName.toString().substringAfterLast(".")
            return when (ext) {
                "tar" -> TarArchiveInputStream(
                    BufferedInputStream(
                        FileInputStream(
                            zipFileName.toFile()
                        )
                    )
                )
                "tar.gz", "tgz", "gz" -> TarArchiveInputStream(
                    GzipCompressorInputStream(
                        BufferedInputStream(
                            FileInputStream(
                                zipFileName.toFile()
                            )
                        )
                    )
                )
                "zip" ->
                    ZipArchiveInputStream(
                        BufferedInputStream(
                            FileInputStream(
                                zipFileName.toFile()
                            )
                        )
                    )
                else -> throw IllegalStateException("Unsupported $ext archive format")
            }
        }

        fun zip(zipFileName: Path, files: Collection<File>) {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFileName.toFile()))).use { out ->
                val data = ByteArray(1024)
                for (file in files) {
                    FileInputStream(file).use { fi ->
                        BufferedInputStream(fi).use { origin ->
                            val entry = ZipEntry(file.name)
                            out.putNextEntry(entry)
                            while (true) {
                                val readBytes = origin.read(data)
                                if (readBytes == -1) {
                                    break
                                }
                                out.write(data, 0, readBytes)
                            }
                        }
                    }
                }
            }
        }

        fun downloadFile(
            url: String,
            path: Path,
            progress: ((readBytes: Long, totalBytes: Long) -> Unit)? = null
        ): Triple<Request, Response, Result<ByteArray, FuelError>> {
            var triple = downloadInternal(url, path, progress)

            val bytes = triple.third.component1()
            if (bytes != null && bytes.isEmpty()) {
                triple = downloadInternal(triple.second.url.toString(), path, progress)
            }
            return triple
        }

        private fun downloadInternal(
            downloadPath: String,
            archive: Path,
            progress: ((readBytes: Long, totalBytes: Long) -> Unit)? = null
        ): Triple<Request, Response, Result<ByteArray, FuelError>> {
            return Fuel.download(downloadPath).destination { response, Url ->
                archive.toFile()
            }.progress { readBytes, totalBytes ->
                if (progress != null) progress(readBytes, totalBytes)
            }.response()
        }
    }
}