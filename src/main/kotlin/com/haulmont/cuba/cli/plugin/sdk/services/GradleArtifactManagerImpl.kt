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

package com.haulmont.cuba.cli.plugin.sdk.services

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.haulmont.cuba.cli.cubaplugin.di.sdkKodein
import com.haulmont.cuba.cli.plugin.sdk.SdkPlugin
import com.haulmont.cuba.cli.plugin.sdk.dto.Classifier
import com.haulmont.cuba.cli.plugin.sdk.dto.MvnArtifact
import com.haulmont.cuba.cli.plugin.sdk.dto.Repository
import com.haulmont.cuba.cli.plugin.sdk.dto.UploadDescriptor
import com.haulmont.cuba.cli.plugin.sdk.gradle.GradleConnector
import com.haulmont.cuba.cli.plugin.sdk.utils.FileUtils
import com.haulmont.cuba.cli.plugin.sdk.utils.copyInputStreamToFile
import com.haulmont.cuba.cli.plugin.sdk.utils.performance
import org.apache.maven.model.Model
import org.apache.maven.model.io.xpp3.MavenXpp3Reader
import org.kodein.di.generic.instance
import java.io.FileInputStream
import java.io.FileReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.logging.Logger


class GradleArtifactManagerImpl : ArtifactManager {

    private val log: Logger = Logger.getLogger(GradleArtifactManagerImpl::class.java.name)

    internal val printWriter: PrintWriter by sdkKodein.instance()
    internal val sdkSettings: SdkSettingsHolder by sdkKodein.instance()
    internal val repositoryManager: RepositoryManager by sdkKodein.instance()
    internal val dbProvider: DbProvider by sdkKodein.instance()

    override fun init() {
        val gradleBuild = Path.of(sdkSettings["gradle.home"]).resolve("build.gradle").also {
            if (!Files.exists(it)) {
                Files.createDirectories(it.parent)
            } else {
                Files.delete(it)
            }
            Files.createFile(it)
        }
        gradleBuild.toFile().copyInputStreamToFile(SdkPlugin::class.java.getResourceAsStream("gradle/build.gradle"))
    }

    override fun readPom(artifact: MvnArtifact, classifier: Classifier): Model? {
        val gradleCoordinates = artifact.gradleCoordinates(classifier)
        log.info("Read POM: $gradleCoordinates")

        var pomFile: Path? = readFromCache(artifact, classifier)

        if (pomFile == null) {
            val pomJson = performance("Read POM") {
                cacheResult(
                    GradleConnector().runTask(
                        "getArtifact", mapOf(
                            "toResolve" to gradleCoordinates,
                            "transitive" to false,
                            "withClassifiers" to false
                        )
                    )
                )
            } ?: return null
            if (!pomJson.asJsonObject.has(artifact.gradleCoordinates())) {
                return null
            }

            pomFile = readFromCache(artifact, classifier)

        }

        if (pomFile != null) {
            if (Files.exists(pomFile)) {
                FileReader(pomFile.toFile()).use {
                    return performance("Read model from POM") {
                        MavenXpp3Reader().read(it)
                    }
                }
            }
        }
        log.info("POM does not exist: ${artifact.gradleCoordinates(classifier)}")
        return null
    }

    private fun readFromCache(artifact: MvnArtifact, classifier: Classifier = Classifier.default()): Path? {
        dbProvider["gradle"][artifact.gradleCoordinates(classifier)].also {
            if (it != null) {
                return Path.of(sdkSettings["gradle.cache"], it)
            }
        }
        return null
    }

    override fun upload(repository: Repository, artifact: MvnArtifact) {
        val descriptors = artifact.classifiers.distinct()
            .filter { it != Classifier.pom() }
            .map { UploadDescriptor(artifact, it, getOrDownloadArtifactFile(artifact, it).toString()) }
            .toList()

        val pomDescriptor = getOrDownloadArtifactFile(artifact, Classifier.pom()).toString()
        try {
            GradleConnector().runTask(
                "publish", mapOf(
                    "toUpload" to Gson().toJson(artifact),
                    "descriptors" to Gson().toJson(descriptors),
                    "pomFile" to pomDescriptor,
                    "targetRepository" to Gson().toJson(repository)
                )
            )
        } catch (e: Exception) {
            throw RuntimeException("Unable to upload ${artifact}. Error: ${e.message}", e)
        }
    }

    override fun getArtifact(artifact: MvnArtifact, classifier: Classifier) {
        cacheResult(
            GradleConnector().runTask(
                "getArtifact", mapOf(
                    "toResolve" to artifact.gradleCoordinates(classifier),
                    "transitive" to false,
                    "withClassifiers" to false
                )
            )
        )
    }

    private fun cacheResult(result: JsonElement?): JsonElement? {
        if (result == null) {
            return result
        }
        val jsonObject = result.asJsonObject
        jsonObject.entrySet().forEach { entry ->
            val coordinates = entry.key.split(":")
            val version = coordinates[2].substringBeforeLast("@")
            val mvnArtifact = MvnArtifact(coordinates[0], coordinates[1], version)

            entry.value.asJsonObject.entrySet().forEach { classifierEntry ->
                val filePath = classifierEntry.value
                if (!filePath.isJsonNull) {
                    val relativePath = Path.of(sdkSettings["gradle.cache"]).relativize(Path.of(filePath.asString))
                    val split = classifierEntry.key.split("@")
                    dbProvider["gradle"][mvnArtifact.gradleCoordinates(Classifier(split[0], split[1]))] =
                        relativePath.toString()
                }
            }
        }
        return result
    }

    private fun readProperties(path: Path): Properties {
        val properties = Properties()
        FileInputStream(path.toFile()).use {
            val inputStreamReader = InputStreamReader(it, StandardCharsets.UTF_8)
            properties.load(inputStreamReader)
        }
        return properties
    }

    override fun getOrDownloadArtifactFile(artifact: MvnArtifact, classifier: Classifier): Path {
        var file: Path? = readFromCache(artifact, classifier)
        if (file == null || !Files.exists(file)) {
            getArtifact(artifact, classifier)
            file = readFromCache(artifact, classifier)
        }
        if (file == null) {
            throw IllegalStateException("Unable to download ${artifact.gradleCoordinates(classifier)}")
        }
        return file
    }

    override fun getOrDownloadArtifactWithClassifiers(artifact: MvnArtifact, classifiers: Collection<Classifier>) {
        val componentsToResolve = mutableListOf<String>()
        for (classifier in classifiers) {
            if (!listOf(Classifier.default(), Classifier.sources(), Classifier.pom()).contains(classifier)) {
                if (readFromCache(artifact, classifier) == null) {
                    componentsToResolve.add(artifact.gradleCoordinates(classifier))
                }
            }
        }
        if (componentsToResolve.isNotEmpty()) {
            cacheResult(
                GradleConnector().runTask(
                    "getArtifact", mapOf(
                        "toResolve" to componentsToResolve.joinToString(separator = ";"),
                        "transitive" to false,
                        "withClassifiers" to false
                    )
                )
            )
        }
    }

    override fun resolve(artifact: MvnArtifact, classifier: Classifier): List<MvnArtifact> {
        val result = cacheResult(
            GradleConnector().runTask(
                "resolve", mapOf(
                    "toResolve" to artifact.gradleCoordinates(classifier)
                )
            )
        ) ?: return emptyList()
        val artifacts = mutableListOf<MvnArtifact>()
        val jsonObject = result.asJsonObject
        jsonObject.entrySet().forEach { entry ->
            val coordinates = entry.key.split(":")
            val mvnArtifact = MvnArtifact(coordinates[0], coordinates[1], coordinates[2])
            entry.value.asJsonObject.entrySet().forEach { classifierEntry ->
                val split = classifierEntry.key.split("@")
                mvnArtifact.classifiers.add(Classifier(split[0], split[1]))
            }
            artifacts.add(mvnArtifact)
        }
        return artifacts
    }

    override fun checkClassifiers(artifact: MvnArtifact) {
        artifact.classifiers.removeAll { !artifactDownloaded(artifact, it) }
    }

    override fun remove(artifact: MvnArtifact) {
        val fromCache = readFromCache(artifact) ?: return
        val artifactPath = fromCache.parent.parent
        if (Files.exists(artifactPath)) {
            FileUtils.deleteDirectory(artifactPath)
        }
    }

    private fun artifactDownloaded(artifact: MvnArtifact, classifier: Classifier): Boolean {
        val fromCache = readFromCache(artifact, classifier)
        return fromCache != null && Files.exists(fromCache)
    }
}