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

package com.haulmont.cli.plugin.sdk.gradle

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.haulmont.cli.core.green
import com.haulmont.cli.core.localMessages
import com.haulmont.cuba.cli.plugin.sdk.di.sdkKodein
import com.haulmont.cuba.cli.plugin.sdk.dto.*
import com.haulmont.cuba.cli.plugin.sdk.services.ArtifactManager
import com.haulmont.cuba.cli.plugin.sdk.services.DbProvider
import com.haulmont.cuba.cli.plugin.sdk.services.RepositoryManager
import com.haulmont.cuba.cli.plugin.sdk.services.SdkSettingsHolder
import com.haulmont.cuba.cli.plugin.sdk.utils.FileUtils
import com.haulmont.cuba.cli.plugin.sdk.utils.copyInputStreamToFile
import com.haulmont.cuba.cli.plugin.sdk.utils.performance
import org.apache.maven.model.Model
import org.apache.maven.model.io.xpp3.MavenXpp3Reader
import org.kodein.di.generic.instance
import java.io.*
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.*
import java.util.logging.Logger

class GradleArtifactManagerImpl : ArtifactManager {

    private val log: Logger = Logger.getLogger(GradleArtifactManagerImpl::class.java.name)

    internal val printWriter: PrintWriter by sdkKodein.instance<PrintWriter>()
    internal val messages by localMessages()
    internal val sdkSettings: SdkSettingsHolder by sdkKodein.instance<SdkSettingsHolder>()
    internal val dbProvider: DbProvider by sdkKodein.instance<DbProvider>()
    internal val repositoryManager: RepositoryManager by sdkKodein.instance<RepositoryManager>()
    internal val gradleConnector by lazy { SdkGradleConnector.instance() }

    override val name = "gradle"

    override fun init() {
        printWriter.println(messages["setup.downloadGradle"])

        val pluginProperties =
            readProperties(GradleResolverPlugin::class.java.getResourceAsStream("application.properties"))
        for (entry in pluginProperties) {
            sdkSettings[entry.key as String] = entry.value as String?
        }

        sdkSettings["gradle.home"] = sdkSettings.sdkHome().resolve("gradle").toString()
        sdkSettings["gradle.cache"] = sdkSettings.sdkHome().resolve(Path.of("gradle", "cache")).toString()
        sdkSettings.flushAppProperties()

        gradleConnector.runTask("wrapper")

        val gradleBuild = Path.of(sdkSettings["gradle.home"]).resolve("build.gradle").also {
            if (!Files.exists(it)) {
                Files.createDirectories(it.parent)
            } else {
                Files.delete(it)
            }
            Files.createFile(it)
        }
        gradleBuild.toFile().copyInputStreamToFile(
            GradleArtifactManagerImpl::class.java.getResourceAsStream("build.gradle")
        )
    }

    private fun readProperties(
        propertiesInputStream: InputStream,
        defaultProperties: Properties = Properties()
    ): Properties {
        val properties = Properties(defaultProperties)
        propertiesInputStream.use {
            val inputStreamReader = InputStreamReader(propertiesInputStream, StandardCharsets.UTF_8)
            properties.load(inputStreamReader)
        }
        return properties
    }

    override fun clean() {
        gradleConnector.runTask("clean")
        Path.of(sdkSettings["gradle.cache"]).also {
            FileUtils.deleteDirectory(it)
            Files.createDirectories(it)
        }
    }

    override fun printInfo() {
        printWriter.println("Gradle path: ${sdkSettings["gradle.home"].green()}")
        printWriter.println("Gradle cache: ${sdkSettings["gradle.cache"].green()}")
    }

    override fun uploadComponentToLocalCache(component: Component): List<MvnArtifact> {
        val dependencies = mutableListOf<MvnArtifact>()
        for (classifier in component.classifiers) {
            val componentPath = Path.of("raw")
                .resolve(component.groupId)
                .resolve(component.artifactId)
                .resolve(component.version)
                .resolve("${component.artifactId}-${component.version}.${classifier.extension}")
            Files.createDirectories(Path.of(sdkSettings["gradle.cache"]).resolve(componentPath).parent)

            component.url?.let { url ->
                val (_, response, _) = FileUtils.downloadFile(
                    url,
                    Path.of(sdkSettings["gradle.cache"]).resolve(componentPath)
                )
                if (response.statusCode == 200) {

                    val mvnArtifact = MvnArtifact(
                        component.groupId,
                        component.artifactId,
                        component.version,
                        classifiers = component.classifiers
                    )
                    dependencies.add(
                        mvnArtifact
                    )
                    dbProvider["gradle"][mvnArtifact.gradleCoordinates(classifier)] = componentPath.toString()
                }
            }
        }
        return dependencies
    }

    override fun readPom(artifact: MvnArtifact, classifier: Classifier): Model? {
        val gradleCoordinates = artifact.gradleCoordinates(classifier)
        log.fine("Read POM: $gradleCoordinates")

        var pomFile: Path? = readFromCache(artifact, classifier)

        if (pomFile == null || !Files.exists(pomFile)) {
            val pomJson = performance("Read POM") {
                cacheResult(
                    gradleConnector.runTask(
                        "getArtifact", mapOf(
                            "toResolve" to gradleCoordinates,
                            if (artifact.groupId.startsWith("io.jmix.")) {
                                "jmixVersion" to artifact.version
                            } else {
                                "jmixVersion" to ""
                            },
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
        log.fine("POM does not exist: ${artifact.gradleCoordinates(classifier)}")
        return null
    }

    private fun readFromCache(
        artifact: MvnArtifact,
        classifier: Classifier = Classifier.jar(),
        isImported: Boolean = false
    ): Path? {
        if (!isImported) {
            dbProvider["gradle"][artifact.gradleCoordinates(classifier)].also {
                if (it != null) {
                    return Path.of(sdkSettings["gradle.cache"], it)
                }
            }
            return null
        }

        val localRepository = repositoryManager.getRepository("sdk-local", RepositoryTarget.SOURCE)
            ?: throw IllegalStateException("\"sdk-local\" source repository does not exists")
        return artifact.localPath(Path.of(localRepository.url), classifier)
    }

    private fun writeToCache(
        artifact: MvnArtifact,
        classifier: Classifier,
        source: Path
    ): Path? {
        val cachedArtifactLocalPathString = dbProvider["gradle"][artifact.gradleCoordinates(classifier)]

        if (cachedArtifactLocalPathString != null) {
            val artifactCachePath = Path.of(sdkSettings["gradle.cache"], cachedArtifactLocalPathString)
            if (!Files.exists(artifactCachePath)) {
                Files.createDirectories(artifactCachePath.parent)
                if (Files.exists(artifactCachePath)) {
                    Files.delete(artifactCachePath)
                }
                Files.createFile(artifactCachePath)
            }

            Files.copy(source, artifactCachePath, StandardCopyOption.REPLACE_EXISTING)

            return artifactCachePath
        }

        log.fine(
            "The path to the ${artifact.gradleCoordinates(classifier)} artifact in the Gradle cache has not " +
                    "been set"
        )

        return null
    }


    override fun upload(repositories: List<Repository>, artifact: MvnArtifact, isImported: Boolean) {
        val descriptors = artifact.classifiers.distinct()
            .filter { it != Classifier.pom() }
            .map {
                if (isImported) {
                    UploadDescriptor(
                        artifact, it,
                        artifactPath(artifact, it, true)
                    )
                } else {
                    UploadDescriptor(
                        artifact, it,
                        artifactPath(artifact, it)
                    )
                }
            }
            .toList()

        var pomDescriptor: String? = null
        if (artifact.classifiers.contains(Classifier.pom())) {
            pomDescriptor = artifactPath(artifact, Classifier.pom(), isImported)
        }
        try {
            gradleConnector.runTask(
                "publish", mapOf(
                    "toUpload" to Gson().toJson(artifact),
                    "descriptors" to
                            if (!descriptors.isEmpty()) {
                                Gson().toJson(descriptors)
                            } else {
                                descriptors.toString()
                            },
                    "pomFile" to pomDescriptor,
                    "targetRepositories" to Gson().toJson(repositories)
                )
            )
        } catch (e: Exception) {
            throw RuntimeException("Unable to upload ${artifact}. Error: ${e.message}", e)
        }
    }

    private fun artifactPath(
        artifact: MvnArtifact,
        it: Classifier,
        isImported: Boolean = false
    ) = (getOrDownloadArtifactFile(artifact, it, isImported)?.toString()
        ?: throw IllegalStateException("Unable to download ${artifact.gradleCoordinates(it)}"))

    override fun getArtifact(artifact: MvnArtifact, classifier: Classifier) {

        cacheResult(
            gradleConnector.runTask(
                "getArtifact", mapOf(
                    "toResolve" to artifact.gradleCoordinates(classifier),
                    if (artifact.groupId.startsWith("io.jmix.")) {
                        "jmixVersion" to artifact.version
                    } else {
                        "jmixVersion" to ""
                    },
                    "transitive" to false,
                    "withClassifiers" to false
                )
            )
        )
    }

    override fun getArtifactFile(artifact: MvnArtifact, classifier: Classifier): Path? =
        readFromCache(artifact, classifier)

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
                    val relativePath =
                        Path.of(sdkSettings["gradle.cache"]).relativize(Path.of(filePath.asString))
                    val split = classifierEntry.key.split("@")
                    dbProvider["gradle"][mvnArtifact.gradleCoordinates(Classifier(split[0], split[1]))] =
                        relativePath.toString()
                }
            }
        }
        return result
    }

    override fun getOrDownloadArtifactFile(
        artifact: MvnArtifact,
        classifier: Classifier,
        isImported: Boolean
    ): Path? {
        var file: Path?
        if (isImported) {
            file = readFromCache(artifact, classifier, true)
//            file = when (file != null) {
//                true -> writeToCache(artifact, classifier, file)
//                false -> null
//            }
        } else {
            file = readFromCache(artifact, classifier)
        }
        if (file == null || !Files.exists(file)) {
            if (artifact.artifactId == "gradle" && artifact.groupId == "gradle") {
                val gradleVersion = artifact.version
                uploadComponentToLocalCache(
                    Component(
                        groupId = "gradle",
                        artifactId = "gradle",
                        url = sdkSettings["gradle.downloadLink"].format(gradleVersion),
                        version = gradleVersion,
                        classifiers = mutableSetOf(Classifier("", "zip"), Classifier(""))
                    )
                )
            } else {
                getArtifact(artifact, classifier)
            }
        }
        if (file == null) {
            file = readFromCache(artifact, classifier)
        }
        return file
    }

    override fun getOrDownloadArtifactWithClassifiers(
        artifact: MvnArtifact,
        classifiers: Collection<Classifier>
    ) {
        val componentsToResolve = mutableListOf<String>()
        for (classifier in classifiers) {
            if (!listOf(Classifier.jar(), Classifier.sources(), Classifier.pom()).contains(classifier)) {
                if (readFromCache(artifact, classifier) == null) {
                    componentsToResolve.add(artifact.gradleCoordinates(classifier))
                }
            }
        }
        if (componentsToResolve.isNotEmpty()) {
            cacheResult(
                gradleConnector.runTask(
                    "getArtifact", mapOf(
                        "toResolve" to componentsToResolve.joinToString(separator = ";"),
                        if (artifact.groupId.startsWith("io.jmix.")) {
                            "jmixVersion" to artifact.version
                        } else {
                            "jmixVersion" to ""
                        },
                        "transitive" to false,
                        "withClassifiers" to false
                    )
                )
            )
        }
    }

    override fun resolve(artifact: MvnArtifact, classifier: Classifier): Collection<MvnArtifact> {
        val result = cacheResult(
            gradleConnector.runTask(
                "resolve", mapOf(
                    "toResolve" to artifact.gradleCoordinates(classifier),
                    if (artifact.groupId.startsWith("io.jmix.")) {
                        "jmixVersion" to artifact.version
                    } else {
                        "jmixVersion" to ""
                    }
                )
            )
        ) ?: return emptyList()
        val artifacts = mutableSetOf<MvnArtifact>()
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