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

import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.Request
import com.github.kittinunf.fuel.core.extensions.AuthenticatedRequest
import com.github.kittinunf.fuel.core.extensions.authentication
import com.haulmont.cuba.cli.cubaplugin.di.sdkKodein
import com.haulmont.cuba.cli.plugin.sdk.dto.MvnArtifact
import com.haulmont.cuba.cli.plugin.sdk.dto.MvnClassifier
import org.apache.maven.model.Model
import org.apache.maven.model.io.xpp3.MavenXpp3Reader
import org.kodein.di.generic.instance
import java.io.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption


class MvnArtifactManagerImpl : MvnArtifactManager {

    private val CLASSIFIERS = arrayOf("sources", "javadoc", "client", "tests")

    internal val printWriter: PrintWriter by sdkKodein.instance()
    internal val sdkSettings: SdkSettingsHolder by sdkKodein.instance()
    internal val mavenExecutor: MavenExecutor by sdkKodein.instance()

    private fun repoUrl(endpoint: String) = sdkSettings.getProperty("sdk-repo-url") + endpoint

    private fun componentUrl(artifact: MvnArtifact): String {
        val groupUrl = artifact.groupId.replace(".", "/")
        val name = artifact.artifactId
        val version = artifact.version
        return "$groupUrl/$name/$version/$name-$version.jar"
    }

    fun InputStream.read(handleResult: (m: String?) -> Unit) {
        InputStreamReader(this).use {
            BufferedReader(it).use {
                var s: String?
                while (it.readLine().also { s = it } != null) {
                    handleResult(s)
                }
            }
        }
    }

    private fun Request.repositoryAuthentication(): Request = AuthenticatedRequest(this)
        .basic(sdkSettings.getProperty("login"), sdkSettings.getProperty("password"))

    override fun readPom(artifact: MvnArtifact): Model? {

        val commandResult = mavenExecutor.mvn(
            "external", "dependency:get",
            arrayListOf("-Dartifact=${artifact.mvnCoordinates(MvnClassifier.pom())}")
        )

        commandResult.result.read { printMvnResultToCommandLine(it) }
        commandResult.error.read { printMvnResultToCommandLine(it) }

        val pomFile = getArtifactPomFile(artifact)

        if (Files.exists(pomFile)) {
            FileReader(pomFile.toFile()).use {
                return MavenXpp3Reader().read(it)
            }
        }
        return null

    }

    private fun printMvnResultToCommandLine(it: String?): String? {
        var s = it
        if (s != null) {
            if (s.startsWith("Progress ")) {
                s = "\r" + s
            } else {
                s += "\n"
            }
        }
        return s
    }

    private fun getArtifactFile(artifact: MvnArtifact, classifier: MvnClassifier): Path {
        var pomDirectory: Path = Path.of(sdkSettings.getProperty("mvn-local-repo"))
        for (groupPart in artifact.groupId.split(".")) {
            pomDirectory = pomDirectory.resolve(groupPart)
        }
        pomDirectory = pomDirectory.resolve(artifact.artifactId).resolve(artifact.version)
        if (!Files.exists(pomDirectory)) {
            Files.createDirectories(pomDirectory)
        }
        val classifierSuffix = if (classifier.type.isEmpty()) "" else "-${classifier.type}"
        return pomDirectory.resolve("${artifact.artifactId}-${artifact.version}${classifierSuffix}.${classifier.extension}")
    }

    override fun upload(artifact: MvnArtifact) {
        if (alreadyUploaded(artifact)) {
            return
        }

        val files = ArrayList<String>()
        val classifiers = ArrayList<String>()
        val types = ArrayList<String>()
        for (classifier in artifact.classifiers) {
            if (!classifier.type.isEmpty()) {
                files.add(getArtifactFile(artifact, classifier).toString())
                classifiers.add(classifier.type)
                types.add(classifier.extension)
            }
        }

        val artifactFile = getArtifactFile(artifact, MvnClassifier.default())
        if (Files.exists(artifactFile)) {

            val tempCopy = Files.createTempFile(
                "temp",
                "${artifact.artifactId}-${artifact.version}.jar"
            )


            Files.copy(artifactFile, tempCopy, StandardCopyOption.REPLACE_EXISTING)

            try {

                val commands = arrayListOf(
                    "-DgroupId=${artifact.groupId}",
                    "-DartifactId=${artifact.artifactId}",
                    "-Dversion=${artifact.version}",
                    "-Dfile=$tempCopy",
                    "-DrepositoryId=sdk.internal",
                    "-Durl=http://localhost:8081/repository/cuba-hosted/",
                    "-Dfiles=" + files.joinToString(),
                    "-Dclassifiers=" + classifiers.joinToString(),
                    "-Dtypes=" + types.joinToString()
                ).also {
                    val artifactPomFile = getArtifactPomFile(artifact)
                    if (Files.exists(artifactPomFile)) {
                        it.add("-DpomFile=" + artifactPomFile.toString())
                        it.add("-DgeneratePom=false")
                    } else {
                        it.add("-DgeneratePom=true")
                    }
                }

                val commandResult = mavenExecutor.mvn(
                    "sdk",
                    "org.apache.maven.plugins:maven-deploy-plugin:3.0.0-M1:deploy-file",
                    commands
                )
                commandResult.result.read {
                    if (it != null) {
                        printMvnResultToCommandLine(it)
                    }
                }
            } finally {
                Files.delete(tempCopy)
            }
        } else {
            printWriter.println("File ${artifactFile} not found!!!")
        }
    }

    private fun alreadyUploaded(artifact: MvnArtifact): Boolean {
        val (_, response, _) = Fuel.head(repoUrl(componentUrl(artifact)))
            .authentication()
            .basic(sdkSettings.getProperty("sdk-repo-login"), sdkSettings.getProperty("sdk-repo-password"))
            .response()
        return response.statusCode == 200
    }

    override fun downloadWithDependencies(artifact: MvnArtifact) {
        downloadToLocalRepoWithDependencies(artifact, MvnClassifier.default())
        for (classifier in CLASSIFIERS) {
            downloadToLocalRepoWithDependencies(artifact, MvnClassifier(classifier))
        }
    }

    private fun downloadToLocalRepoWithDependencies(artifact: MvnArtifact, classifier: MvnClassifier) {
        getArtifact(artifact, classifier)
        resolveDependencies(artifact, classifier)
    }

    private fun resolveDependencies(artifact: MvnArtifact, classifier: MvnClassifier) {
        val commandResult = mavenExecutor.mvn(
            "external",
            "dependency:resolve",
            arrayListOf(
                "-Dartifact=${artifact.mvnCoordinates(classifier)}",
                "-Dtransitive=true",
                "-Dclassifier=${classifier.type}",
                "-f", getArtifactPomFile(artifact).toString()
            )
        )
        commandResult.result.read {
            if (it != null) {
                printMvnResultToCommandLine(it)
            }
        }
    }

    private fun getArtifact(artifact: MvnArtifact, classifier: MvnClassifier) {
        val commandResult = mavenExecutor.mvn(
            "external",
            "dependency:get",
            arrayListOf(
                "-Dartifact=${artifact.mvnCoordinates(classifier)}"
            )
        )
        commandResult.result.read {
            if (it != null) {
                printMvnResultToCommandLine(it)
            }
        }
    }

    override fun findDependencies(artifact: MvnArtifact): List<MvnArtifact>? {
        readPom(artifact) ?: return null

        val pomFile = getArtifactPomFile(artifact)
        val commandResult = mavenExecutor.mvn(
            "external",
            "dependency:list",
            arrayListOf("-f", "\"$pomFile\"")
        )

        val artifacts = ArrayList<MvnArtifact>()
        var artifactList = false
        commandResult.result.read {
            if (it != null) {
                printMvnResultToCommandLine(it)

                if (it.contains("Finished at: ")) {
                    artifactList = false
                }

                if (artifactList) {
                    val dependency = readArtifactFromLine(it)
                    if (dependency != null) {
                        artifacts.add(dependency)
                    }
                }

                if (it.contains("The following files have been resolved:")) {
                    artifactList = true
                }
            }
        }
        commandResult.error.read { printMvnResultToCommandLine(it) }

        return artifacts
    }

    override fun resolveClassifiers(artifact: MvnArtifact) {
        if (artifactDownloaded(artifact, MvnClassifier.default())) {
            artifact.classifiers.add(MvnClassifier.default())
        }

        if (artifactDownloaded(artifact, MvnClassifier.pom())) {
            artifact.classifiers.add(MvnClassifier.pom())
        }

        for (classifier in CLASSIFIERS) {
            val mvnClassifier = MvnClassifier(classifier)
            if (artifactDownloaded(artifact, mvnClassifier)) {
                artifact.classifiers.add(mvnClassifier)
            }
        }
    }

    private fun artifactDownloaded(artifact: MvnArtifact, classifier: MvnClassifier): Boolean {
        return Files.exists(getArtifactFile(artifact, classifier))
    }

    private fun getArtifactPomFile(artifact: MvnArtifact) =
        getArtifactFile(artifact, MvnClassifier.pom())

    private fun readArtifactFromLine(line: String?): MvnArtifact? {
        if (line == null) {
            return null;
        }
        val formattedLine = line.replace("[INFO] ", "")
        formattedLine.split(":").let {
            if (it.size != 5) {
                return null;
            }
            return MvnArtifact(it[0].trim(), it[1].trim(), it[3].trim())
        }
    }

}