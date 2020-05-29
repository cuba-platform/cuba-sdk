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

package com.haulmont.cli.plugin.sdk.maven

import com.haulmont.cli.core.green
import com.haulmont.cli.core.localMessages
import com.haulmont.cli.core.red
import com.haulmont.cli.plugin.sdk.maven.di.mavenSdkKodein
import com.haulmont.cuba.cli.plugin.sdk.commands.AbstractSdkCommand
import com.haulmont.cuba.cli.plugin.sdk.commands.repository.ToolInstaller
import com.haulmont.cuba.cli.plugin.sdk.di.sdkKodein
import com.haulmont.cuba.cli.plugin.sdk.dto.*
import com.haulmont.cuba.cli.plugin.sdk.services.ArtifactManager
import com.haulmont.cuba.cli.plugin.sdk.services.RepositoryManager
import com.haulmont.cuba.cli.plugin.sdk.services.SdkSettingsHolder
import com.haulmont.cuba.cli.plugin.sdk.utils.FileUtils
import com.haulmont.cuba.cli.plugin.sdk.utils.performance
import org.apache.maven.model.Model
import org.apache.maven.model.io.xpp3.MavenXpp3Reader
import org.kodein.di.generic.instance
import java.io.FileReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.PrintWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.*
import java.util.logging.Logger
import kotlin.collections.ArrayList
import kotlin.concurrent.thread


class MvnArtifactManagerImpl : ArtifactManager {

    private val log: Logger = Logger.getLogger(MvnArtifactManagerImpl::class.java.name)

    internal val messages by localMessages()
    internal val printWriter: PrintWriter by sdkKodein.instance<PrintWriter>()
    internal val sdkSettings: SdkSettingsHolder by sdkKodein.instance<SdkSettingsHolder>()
    internal val repositoryManager: RepositoryManager by sdkKodein.instance<RepositoryManager>()
    internal val mvnExecutor: MavenExecutor by mavenSdkKodein.instance<MavenExecutor>()

    override val name = "maven"

    override fun init() {
        printWriter.println(messages["setup.initMaven"])

        val pluginPropertyies =
            readProperties(MavenResolverPlugin::class.java.getResourceAsStream("application.properties"))
        for (entry in pluginPropertyies) {
            sdkSettings[entry.key as String] = entry.value as String?
        }

        sdkSettings["maven.settings"] = sdkSettings.sdkHome().resolve("sdk-settings.xml").toString()
        sdkSettings["maven.local.repo"] = sdkSettings.sdkHome().resolve(".m2").toString()
        sdkSettings["maven.path"] = sdkSettings.sdkHome().resolve("mvn").toString()
        sdkSettings.flushAppProperties()
        downloadAndConfigureMaven()
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

    private fun downloadAndConfigureMaven() {
        ToolInstaller(
            "Maven",
            mavenDownloadLink(),
            sdkSettings.sdkHome().resolve(sdkSettings["maven.path"]),
            true
        ).downloadAndConfigure(
            configure = {
                mvnExecutor.buildMavenSettingsFile()
                val thread = thread {
                    mvnExecutor.init()
                }
                AbstractSdkCommand.waitTask(messages["setup.maven.configuration"], 500) {
                    thread.isAlive
                }
            },
            onFail = {
                printWriter.println(
                    messages["setup.maven.configurationFailed"].format(it.message).red()
                )
            }
        )


    }

    private fun mavenDownloadLink() = sdkSettings["maven.downloadLink"]
        .format(
            sdkSettings["maven.version"],
            sdkSettings["maven.version"]
        )

    override fun clean() {
        Path.of(sdkSettings["maven.local.repo"]).also {
            FileUtils.deleteDirectory(it)
            Files.createDirectories(it)
        }
    }

    override fun printInfo() {
        printWriter.println("Maven install path: ${sdkSettings["maven.path"].green()}")
        printWriter.println("Maven local repository: ${sdkSettings["maven.local.repo"].green()}")
    }

    override fun uploadComponentToLocalCache(component: Component): List<MvnArtifact> {
        val dependencies = mutableListOf<MvnArtifact>()
        for (classifier in component.classifiers) {
            val componentPath = Path.of(sdkSettings["maven.local.repo"])
                .resolve(component.groupId)
                .resolve(component.artifactId)
                .resolve(component.version)
                .resolve("${component.artifactId}-${component.version}.${classifier.extension}")
            Files.createDirectories(componentPath.parent)
            component.url?.let { url ->
                val (_, response, _) = FileUtils.downloadFile(
                    url,
                    componentPath
                )
                if (response.statusCode == 200) {
                    dependencies.add(
                        MvnArtifact(
                            component.groupId,
                            component.artifactId,
                            component.version,
                            classifiers = component.classifiers
                        )
                    )
                }
            }
        }
        return dependencies
    }

    override fun readPom(artifact: MvnArtifact, classifier: Classifier): Model? {
        log.info("Read POM: ${artifact.mvnCoordinates(classifier)}")
        val pomFile = getArtifactPomFile(artifact, classifier)

        performance("Read POM") {
            if (!Files.exists(pomFile)) {
                mvnExecutor.mvn(
                    RepositoryTarget.SOURCE.getId(),
                    "org.apache.maven.plugins:maven-dependency-plugin:3.1.1:get",
                    arrayListOf("-Dartifact=${artifact.mvnCoordinates(classifier)}"),
                    ignoreErrors = true
                )
            }
        }

        if (Files.exists(pomFile)) {
            FileReader(pomFile.toFile()).use {
                return performance("Read model from POM") {
                    MavenXpp3Reader().read(it)
                }
            }
        }
        log.info("POM does not exist: ${artifact.mvnCoordinates(classifier)}")
        return null
    }

    private fun getArtifactFile(artifact: MvnArtifact, classifier: Classifier = Classifier.default()): Path {
        return artifact.localPath(Path.of(sdkSettings["maven.local.repo"]), classifier).also {
            val parent = it.parent
            if (!Files.exists(parent)) {
                Files.createDirectories(parent)
            }
        }
    }

    override fun upload(repositories: List<Repository>, artifact: MvnArtifact) {
        repositories.forEach {
            uploadToRepository(it, artifact)
        }
    }

    private fun uploadToRepository(repository: Repository, artifact: MvnArtifact) {
        val mainClassifier = artifact.mainClassifier()
        log.info("Uploading: ${artifact.mvnCoordinates(mainClassifier)}")

        val files = ArrayList<String>()
        val classifiers = ArrayList<String>()
        val types = ArrayList<String>()
        for (classifier in artifact.classifiers) {
            if (classifier.type.isNotEmpty() || classifier.extension == "sdk") {
                files.add(getOrDownloadArtifactFile(artifact, classifier).toString())
                classifiers.add(classifier.type)
                types.add(classifier.extension)
            }
        }

        log.fine("Classifiers to upload: ${artifact.mvnCoordinates(mainClassifier)}:${classifiers.joinToString()}")

        val artifactFile = getOrDownloadArtifactFile(artifact, mainClassifier)

        //        printWriter.println("Upload ${artifact.mvnCoordinates(mainClassifier)}, file $artifactFile")
        if (Files.exists(artifactFile)) {

            val tempCopy = Files.createTempFile(
                "temp",
                "${artifact.artifactId}-${artifact.version}.${mainClassifier.extension}"
            )

            Files.copy(artifactFile, tempCopy, StandardCopyOption.REPLACE_EXISTING)
            log.fine("Create temp file for ${artifact.mvnCoordinates(mainClassifier)}: ${tempCopy}")
            try {

                val commands = arrayListOf(
                    "-DgroupId=${artifact.groupId}",
                    "-DartifactId=${artifact.artifactId}",
                    "-Dversion=${artifact.version}",
                    "-Dpackaging=${mainClassifier.extension}",
                    "-Dfile=$tempCopy",
                    "-DrepositoryId=${repositoryManager.getRepositoryId(RepositoryTarget.TARGET, repository.name)}",
                    "-DgeneratePom=false",
                    "-Dpackaging=${mainClassifier.extension}",
                    "-Durl=${repository.url}"
                ).also {

                    val artifactPomFile = getArtifactPomFile(artifact)
                    if (mainClassifier.type.isNotEmpty()) {
                        it.add("-Dclassifier=${mainClassifier.type}")
                    }
                    if (Files.exists(artifactPomFile)) {
                        it.add("-DpomFile=$artifactPomFile")
                        it.add("-DgeneratePom=false")
                    } else {
                        it.add("-DgeneratePom=true")
                    }

                    if (files.isNotEmpty()) {
                        it.add("-Dfiles=\"${files.joinToString(",")}\"")
                        it.add("-Dclassifiers=\"${classifiers.joinToString(",")}\"")
                        it.add("-Dtypes=\"${types.joinToString(",")}\"")
                    }
                }

                val commandResult = mvnExecutor.mvn(
                    RepositoryTarget.TARGET.getId(),
                    "org.apache.maven.plugins:maven-deploy-plugin:3.0.0-M1:deploy-file",
                    commands
                )

            } finally {
                Files.delete(tempCopy)
                log.fine("Deleted temp file for ${artifact.mvnCoordinates(mainClassifier)}: ${tempCopy}")
            }
        } else {
            throw IllegalStateException("File ${artifactFile} not found")
        }
    }

    override fun resolve(artifact: MvnArtifact, classifier: Classifier): List<MvnArtifact> {
        if (listOf("jar", "pom", "sdk").contains(classifier.extension)) {
            return artifact.pomClassifiers().flatMap { resolveDependencies(artifact, classifier, it) }.toList()
        } else {
            return ArrayList()
        }
    }

    private fun resolveDependencies(
        artifact: MvnArtifact,
        classifier: Classifier,
        pomClassifier: Classifier = Classifier.pom()
    ): List<MvnArtifact> {
        log.info("Resolve dependencies ${artifact.mvnCoordinates(classifier)}")
        val commandResult = mvnExecutor.mvn(
            RepositoryTarget.SOURCE.getId(),
            "dependency:resolve",
            arrayListOf(
                "-Dtransitive=true",
                "-DincludeParents=true",
                "-DoverWriteSnapshots=true",
                "-Dclassifier=${classifier.type}",
                "-f", getArtifactPomFile(artifact, pomClassifier).toString()
            )
        )
        val artifacts = ArrayList<MvnArtifact>()
        var artifactList = false

        commandResult.split("\n").forEach {
            if (it.trim() == "[INFO]") {
                artifactList = false
            }

            if (artifactList) {
                val dependency = readArtifactFromLine(it)
                if (dependency != null) {
                    log.fine("Read artifact from line \"$it\": $dependency")
                    artifacts.add(dependency)
                }
            }

            if (it.contains("The following files have been resolved:")) {
                artifactList = true
            }
        }

        return artifacts
    }

    override fun getArtifact(artifact: MvnArtifact, classifier: Classifier) {
        log.info("Get with dependencies ${artifact.mvnCoordinates(classifier)}")
        performance("Get artifact") {
            mvnExecutor.mvn(
                RepositoryTarget.SOURCE.getId(),
                "org.apache.maven.plugins:maven-dependency-plugin:3.1.1:get",
                arrayListOf(
                    "-Dartifact=${artifact.mvnCoordinates(classifier)}"
                ),
                ignoreErrors = true
            )
        }
    }

    override fun checkClassifiers(artifact: MvnArtifact) {
        artifact.classifiers.removeAll { !artifactDownloaded(artifact, it) }
    }

    override fun remove(artifact: MvnArtifact) {
        val artifactPath = getArtifactFile(artifact).parent
        if (Files.exists(artifactPath)) {
            FileUtils.deleteDirectory(artifactPath)
        }
    }

    private fun artifactDownloaded(artifact: MvnArtifact, classifier: Classifier): Boolean {
        return Files.exists(getArtifactFile(artifact, classifier))
    }

    private fun getArtifactPomFile(artifact: MvnArtifact, pomClassifier: Classifier = Classifier.pom()) =
        getArtifactFile(artifact, pomClassifier)

    override fun getOrDownloadArtifactFile(
        artifact: MvnArtifact,
        classifier: Classifier
    ): Path {
        val file = getArtifactFile(artifact, classifier)
        if (!Files.exists(file)) {
            log.info("Download artifact again ${artifact.mvnCoordinates(classifier)}")
            getArtifact(artifact, classifier)
        }
        return file
    }

    override fun getOrDownloadArtifactWithClassifiers(artifact: MvnArtifact, classifiers: Collection<Classifier>) {
        for (classifier in classifiers) {
            performance("Read classifier $classifier") {
                getOrDownloadArtifactFile(artifact, classifier)
            }
        }
    }

    private fun readArtifactFromLine(line: String?): MvnArtifact? {
        if (line == null) {
            return null
        }
        val formattedLine = line.replace("[INFO] ", "")
        formattedLine.split(":").let {

            if (it.size < 4) {
                return null
            }

            return MvnArtifact(
                it[0].trim(),
                it[1].trim(),
                when (it.size) {
                    4, 5 -> it[3].trim()
                    else -> it[4].trim()
                }
            )
        }
    }

}