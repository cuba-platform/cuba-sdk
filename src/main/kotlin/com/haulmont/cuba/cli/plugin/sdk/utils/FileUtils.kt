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

import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile

class FileUtils {
    companion object {
        fun unzip(zipFileName: Path, targetDir: Path): Path {
            ZipFile(zipFileName.toFile()).use { zip ->
                zip.entries().asSequence().forEach { entry ->
                    zip.getInputStream(entry).use { input ->
                        targetDir.resolve(entry.name).also {
                            Files.createDirectories(it.parent)
                            if (Files.exists(it)) {
                                Files.delete(it)
                            }
                        }.also {
                            Files.createFile(it)
                                .toFile().outputStream().use { output ->
                                    input.copyTo(output)
                                }
                        }
                    }
                }
            }
            return targetDir
        }
    }
}