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
import java.io.*
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
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
            zipFileName: Path, targetDir: Path, skipFirstEntry: Boolean = false,
            progressFun: UnzipProcessCallback? = null
        ): Path {
            var firstZipEntry: ZipEntry? = null;
            ZipFile(zipFileName.toFile()).use { zip ->
                val total = zip.entries().asSequence().count()
                var count = 0
                zip.entries().asSequence().forEach { entry ->
                    if (firstZipEntry == null) {
                        firstZipEntry = entry
                    }
                    zip.getInputStream(entry).use { input ->
                        var entryName = entry.name
                        if (skipFirstEntry && firstZipEntry != null) {
                            entryName = entryName.replaceFirst(firstZipEntry!!.getName(), "")
                        }
                        targetDir.resolve(entryName).also {
                            Files.createDirectories(it.parent)
                            if (Files.exists(it)) {
                                Files.delete(it)
                            }
                        }.also {
                            if (!entry.isDirectory) {
                                Files.createFile(it)
                                    .toFile().outputStream().use { output ->
                                        input.copyTo(output)
                                    }
                            }
                        }
                    }

                    progressFun?.let { it(++count, total) }
                }
            }
            return targetDir
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