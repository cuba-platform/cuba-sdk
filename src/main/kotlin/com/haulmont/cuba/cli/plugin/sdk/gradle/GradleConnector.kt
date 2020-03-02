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

package com.haulmont.cuba.cli.plugin.sdk.gradle

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.haulmont.cuba.cli.cubaplugin.di.sdkKodein
import com.haulmont.cuba.cli.plugin.sdk.commands.CommonSdkParameters
import com.haulmont.cuba.cli.plugin.sdk.dto.RepositoryTarget
import com.haulmont.cuba.cli.plugin.sdk.services.RepositoryManager
import com.haulmont.cuba.cli.plugin.sdk.services.SdkSettingsHolder
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProgressEvent
import org.gradle.tooling.model.GradleProject
import org.kodein.di.generic.instance
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintWriter
import java.nio.file.Files
import java.nio.file.Path

typealias ProgressCallback = (event: ProgressEvent?) -> Unit

class GradleConnector() {
    private val connector: GradleConnector
    private val sdkSettings: SdkSettingsHolder by sdkKodein.instance()
    private val repositoryManager: RepositoryManager by sdkKodein.instance()
    private val printWriter: PrintWriter by sdkKodein.instance()

    init {
        connector = GradleConnector.newConnector()
        val gradleHome = Path.of(sdkSettings["gradle.home"]).also {
            if (!Files.exists(it)) {
                Files.createDirectories(it)
            }
        }.toFile()
        connector.useGradleVersion(sdkSettings["gradle.version"])
        connector.useGradleUserHomeDir(File(sdkSettings["gradle.home"]))
        connector.useBuildDistribution()
        connector.forProjectDirectory(gradleHome)
    }

    fun runTask(
        name: String, params: Map<String, Any?> = mapOf(),
        progressFun: ProgressCallback? = null
    ): JsonElement? {
        val connection = connector.connect()
        connection.model(GradleProject::class.java)
        try {
            val outputStream = ByteArrayOutputStream()
            if (CommonSdkParameters.info) {
                printWriter.println()
            }
            val repositoriesJson = Gson().toJson(repositoryManager.getRepositories(RepositoryTarget.SOURCE))
            val buildLauncher = connection.newBuild()
                .withArguments(params.filter { it.value != null }.map { "-P${it.key}=${it.value}" }.toList())
                .addArguments("-g", sdkSettings["gradle.cache"])
                .addArguments("-PsdkRepositories=${repositoriesJson}")
                .addArguments("-Dorg.gradle.parallel=true")
                .forTasks(name)
                .setStandardOutput(outputStream)

            CommonSdkParameters.gradleOptions?.let { list ->
                list.forEach {
                    buildLauncher.addArguments(it)
                }
            }

            if (progressFun != null) {
                buildLauncher.addProgressListener(progressFun)
            }
            if (CommonSdkParameters.info) {
                buildLauncher.addArguments("--info")
                buildLauncher.addProgressListener { event: ProgressEvent? ->
                    printWriter.println(event?.description)
                }
            }
            buildLauncher.run()
            if (CommonSdkParameters.info) {
                printWriter.println()
            }
            val stringResult = outputStream.toString()
            if (stringResult.contains("<JSON>") && stringResult.contains("</JSON>")) {
                val json = stringResult.substringAfter("<JSON>").substringBefore("</JSON>")
                return Gson().fromJson<JsonElement>(json, JsonElement::class.java)
            } else {
                return null
            }

        } finally {
            connection.close()
        }
    }


}