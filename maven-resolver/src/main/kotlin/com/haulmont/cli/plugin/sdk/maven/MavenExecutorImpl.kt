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

package com.haulmont.cli.plugin.sdk.maven

import com.haulmont.cli.core.commands.CommonParameters
import com.haulmont.cuba.cli.plugin.sdk.SdkPlugin
import com.haulmont.cuba.cli.plugin.sdk.commands.CommonSdkParameters
import com.haulmont.cuba.cli.plugin.sdk.di.sdkKodein
import com.haulmont.cuba.cli.plugin.sdk.dto.OsType
import com.haulmont.cuba.cli.plugin.sdk.dto.RepositoryTarget
import com.haulmont.cuba.cli.plugin.sdk.services.RepositoryManager
import com.haulmont.cuba.cli.plugin.sdk.services.SdkSettingsHolder
import com.haulmont.cuba.cli.plugin.sdk.utils.copyInputStreamToFile
import com.haulmont.cuba.cli.plugin.sdk.utils.currentOsType
import org.kodein.di.generic.instance
import org.redundent.kotlin.xml.Node
import org.redundent.kotlin.xml.xml
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.PrintWriter
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.logging.Logger

class MavenExecutorImpl : MavenExecutor {

    private val log: Logger = Logger.getLogger(MavenExecutorImpl::class.java.name)
    private val sdkSettings: SdkSettingsHolder by sdkKodein.instance<SdkSettingsHolder>()
    private val repositoryManager: RepositoryManager by sdkKodein.instance<RepositoryManager>()
    private val printWriter: PrintWriter by sdkKodein.instance<PrintWriter>()

    private fun mvnSettingsPath() = Path.of(sdkSettings["maven.settings"])

    override fun init() {
        val emptyPomFile = Files.createTempFile("empty-pom", "xml").toFile()
        emptyPomFile.copyInputStreamToFile(SdkPlugin::class.java.getResourceAsStream("empty-pom.xml"))
        mvn(
            RepositoryTarget.SOURCE.getId(),
            "dependency:resolve-plugins",
            arrayListOf(
                "-Dtransitive=true",
                "-DincludeParents=true",
                "-f", emptyPomFile.toString()
            )
        )
    }

    override fun mvn(profile: String, command: String, commands: List<String>, ignoreErrors: Boolean): String {
        val rt = Runtime.getRuntime()
        val settingsFile = mvnSettingFile()
        val cliCommandsList = ArrayList(
            arrayOf(
                Path.of(
                    sdkSettings["maven.path"],
                    "bin",
                    mavenCmd()
                ).toString(),
                command,
                "-s", "\"$settingsFile\"",
                "-P", profile,
                "-Dmaven.test.skip", "-DskipTests"
            ).asList()
        )
        cliCommandsList.addAll(commands)
        if (CommonParameters.stacktrace) {
            cliCommandsList.add("-e")
        }

        if (!CommonSdkParameters.singleThread) {
            cliCommandsList.add("-T 1C")
        }

        CommonSdkParameters.resolverOptions?.let { cliCommandsList.addAll(it) }

        val cliCommands = arrayOfNulls<String>(cliCommandsList.size)
        cliCommandsList.toArray(cliCommands)

        log.fine("Execute maven:${cliCommands.joinToString(separator = " ")}")
        val proc = rt.exec(cliCommands)

        val commandOutput = StringBuilder()
        if (CommonSdkParameters.info) {
            printWriter.println()
        }
        proc.inputStream.readMvn {
            if (it != null) {
                val output = it.mvnFormatOutput()
                if (CommonSdkParameters.info) {
                    printWriter.print(output)
                }
                commandOutput.append(output)
            }
        }
        if (CommonSdkParameters.info) {
            printWriter.println()
        }

        val commandResult = commandOutput.toString()
        if (!ignoreErrors) {
            if (commandResult.contains("[BUILD FAILURE]") || commandResult.contains("[ERROR]")) {
                throw IllegalStateException("Maven execution failed. \n$commandResult")
            }
        }
        return commandResult
    }

    private fun mavenCmd() = when (currentOsType()) {
        OsType.WINDOWS -> "mvn.cmd"
        else -> "mvn"
    }

    fun InputStream.readMvn(handleResult: (m: String?) -> Unit) {
        InputStreamReader(this).use {
            BufferedReader(it).use {
                var s: String? = null
                while (it.readLine().also { s = it } != null) {
                    handleResult(s)
                }
            }
        }
    }

    fun String.mvnFormatOutput(): String? {
        val s = if (this.startsWith("Progress ")) "\r" + this else this + "\n"
        Logger.getLogger(MavenExecutorImpl::class.java.name).fine("mvn:$s")
        return s
    }

    private fun mvnSettingFile(): Path {
        return mvnSettingsPath().also {
            if (!Files.exists(it)) {
                buildMavenSettingsFile()
            }
        }
    }

    override fun buildMavenSettingsFile() {
        val settings = xml("settings") {
            "localRepository" {
                -sdkSettings["maven.local.repo"]
            }
            "profiles" {
                "profile" {
                    "id" { -RepositoryTarget.SOURCE.getId() }
                    "activation" {
                        "activeByDefault" { -"true" }
                    }
                    this.addNode(addRepositories("repositories", "repository", RepositoryTarget.SOURCE))
                    this.addNode(addRepositories("pluginRepositories", "pluginRepository", RepositoryTarget.SOURCE))
                }
                "profile" {
                    "id" { -RepositoryTarget.TARGET.getId() }
                    "activation" {
                        "activeByDefault" { -"false" }
                    }
                    "properties" {
                        "downloadSources" { -"true" }
                        "downloadJavadocs" { -"true" }
                    }
                    this.addNode(addRepositories("repositories", "repository", RepositoryTarget.TARGET))
                }
            }
            "servers" {
                listOf(RepositoryTarget.SOURCE, RepositoryTarget.TARGET).forEach { target ->
                    repositoryManager.getRepositories(target)
                        .filter { it.authentication != null }
                        .forEach {
                            "server" {
                                "id" { -repositoryManager.getRepositoryId(target, it.name) }
                                "username" { -it.authentication!!.login }
                                "password" { -it.authentication!!.password }
                            }
                        }
                }
            }
        }

        writeToFile(mvnSettingsPath(), settings.toString(true))
    }

    private fun writeToFile(file: Path, text: String) {
        if (!Files.exists(file)) {
            Files.createFile(file)
        }
        Files.writeString(
            file,
            text,
            StandardOpenOption.WRITE,
            StandardOpenOption.TRUNCATE_EXISTING
        )
    }

    private fun addRepositories(rootEl: String, elementName: String, target: RepositoryTarget): Node =
        xml(rootEl) {
            repositoryManager.getRepositories(target).forEach {
                elementName {
                    "id" { -repositoryManager.getRepositoryId(target, it.name) }
                    "name" { -it.name }
                    "url" { -it.url }
                }
            }
        }

}