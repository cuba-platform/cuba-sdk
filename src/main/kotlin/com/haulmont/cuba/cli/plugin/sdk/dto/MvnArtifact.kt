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

package com.haulmont.cuba.cli.plugin.sdk.dto

import java.nio.file.Files
import java.nio.file.Path


data class MvnArtifact(
    val groupId: String,
    val artifactId: String,
    val version: String,

    val classifiers: MutableList<Classifier> = ArrayList()
) {
    fun mvnCoordinates(classifier: Classifier? = null): String {
        var coordinates = "${groupId}:${artifactId}:${version}"
        if (classifier != null) {
            coordinates += ":${classifier.extension}:${classifier.type}"
        }
        return coordinates
    }

    fun mainClassifier(): Classifier {
        for (classifier in classifiers) {
            if (classifier.type == "" && classifier.extension != "pom") {
                return classifier
            }
        }
        for (classifier in classifiers) {
            if (classifier.extension != "pom") {
                return classifier
            }
        }
        return Classifier.pom()
    }

    fun pomClassifiers(): List<Classifier> =
        classifiers.filter { it.extension == "pom" || it.extension == "sdk" }.ifEmpty { listOf(Classifier.pom()) }

    fun localPath(repoPath: Path, classifier: Classifier = Classifier.default()): Path {
        var path: Path = repoPath
        for (groupPart in groupId.split(".")) {
            path = path.resolve(groupPart)
        }
        path = path.resolve(artifactId).resolve(version)
        if (!Files.exists(path)) {
            Files.createDirectories(path)
        }
        val classifierSuffix = if (classifier.type.isEmpty()) "" else "-${classifier.type}"
        return path.resolve("${artifactId}-${version}${classifierSuffix}.${classifier.extension}")
    }
}