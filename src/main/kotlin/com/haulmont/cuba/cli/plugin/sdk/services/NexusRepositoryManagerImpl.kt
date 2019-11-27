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
import com.haulmont.cuba.cli.cubaplugin.di.sdkKodein
import com.haulmont.cuba.cli.kodein
import com.haulmont.cuba.cli.plugin.sdk.dto.Artifact
import org.apache.maven.model.Model
import org.apache.maven.model.io.xpp3.MavenXpp3Reader
import org.kodein.di.generic.instance
import java.io.*
import java.nio.file.Files
import java.nio.file.Path


class NexusRepositoryManagerImpl : NexusRepositoryManager {

    internal val printWriter: PrintWriter by kodein.instance()
    internal val sdkSettings: SdkSettingsHolder by sdkKodein.instance()
    internal val mavenExecutor: MavenExecutor by sdkKodein.instance()

    private fun repoUrl(endpoint: String) = sdkSettings.getProperty("url") + "repository/cuba-work/" + endpoint
    private fun componentUrl(artifact: Artifact): String {
        val groupUrl = artifact.group.replace(".", "/")
        val extensionUrl = if (artifact.classifier != null) "-$artifact.classifier" else ""
        val name = artifact.name
        val version = artifact.version
        val type = artifact.type
        return "$groupUrl/$name/$version/$name-$version$extensionUrl.$type"
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

    override fun readPom(artifact: Artifact): Model? {

        printWriter.println("Read pom: $artifact")

        val commandResult = mavenExecutor.mvn(
            "external", "dependency:get",
            arrayListOf("-Dartifact=${artifact.group}:${artifact.name}:${artifact.version}:pom")
        )

        commandResult.result.read { printMvnResultToCommandLine(it) }
        commandResult.error.read { printMvnResultToCommandLine(it) }

        val pomFile = getArtifactPomFile(artifact)

        FileReader(pomFile.toFile()).use {
            return MavenXpp3Reader().read(it)
        }

    }

    private fun printMvnResultToCommandLine(it: String?) {
        var s = it
        if (s != null) {
            if (s.startsWith("Progress ")) {
                s = "\r" + s
            } else {
                s += "\n"
            }
        }
        printWriter.print(s)
    }

    private fun getArtifactPomFile(artifact: Artifact): Path {
        var pomDirectory: Path = Path.of(sdkSettings.getProperty("mvn-local-repo"))
        for (groupPart in artifact.group.split(".")) {
            pomDirectory = pomDirectory.resolve(groupPart)
        }
        pomDirectory = pomDirectory.resolve(artifact.name).resolve(artifact.version)
        if (!Files.exists(pomDirectory)) {
            Files.createDirectories(pomDirectory)
        }
        return pomDirectory.resolve("${artifact.name}-${artifact.version}.pom")
    }

    override fun fetchFile(artifact: Artifact) {
        printWriter.println("Fetch file: $artifact")

        Fuel.download(repoUrl(componentUrl(artifact)))
            .fileDestination { response, Url ->
                Files.createTempFile(
                    "temp",
                    "$artifact.name-$artifact.version-$artifact.classifier.$artifact.extension"
                ).toFile()
            }.repositoryAuthentication().response()
    }

    override fun fetchWithDependencies(artifact: Artifact) {
        val pomArtifact = Artifact(artifact.group, artifact.name, artifact.version, null, "pom")
        val dependencies = findDependencies(pomArtifact)
        dependencies.forEach { fetchFile(it) }
    }

    override fun findDependencies(artifact: Artifact): List<Artifact> {
        readPom(artifact)

        printWriter.println("Find dependencies: $artifact")
        val pomFile = getArtifactPomFile(artifact)
        val commandResult = mavenExecutor.mvn("external", "dependency:list", arrayListOf("-f", "\"$pomFile\""))

        val artifacts = ArrayList<Artifact>()
        var artifactList = false
        commandResult.result.read {
            if (it != null) {
                printMvnResultToCommandLine(it)

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

    private fun readArtifactFromLine(line: String?): Artifact? {
        if (line == null) {
            return null;
        }
        val formattedLine = line.replace("[INFO] ", "")
        formattedLine.split(":").let {
            if (it.size != 5) {
                return null;
            }
            return Artifact(it[0].trim(), it[1].trim(), it[3].trim())
        }
    }

    fun readDependencies(artifact: Artifact, dependencies: MutableSet<Artifact>): MutableSet<Artifact> {
        val model: Model? = readPom(artifact)
        if (!dependencies.contains(artifact)) {
            dependencies.add(artifact)
            if (model != null) {
                for (dependency in model.dependencies) {
                    if (dependency.version != null) {
                        val pomArtifact = Artifact(dependency, null, "pom")
                        if (!dependencies.contains(pomArtifact)) {
                            for (classifier in arrayOf("sources", "javadoc", "client", "tests")) {
                                dependencies.add(Artifact(dependency, classifier))
                            }
                            dependencies.addAll(readDependencies(pomArtifact, dependencies))
                        }
                    }
                }
            }
        }
        return dependencies;
    }


}