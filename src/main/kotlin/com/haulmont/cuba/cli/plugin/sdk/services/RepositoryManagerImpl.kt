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
import com.google.gson.internal.LinkedTreeMap
import com.google.gson.reflect.TypeToken
import com.haulmont.cuba.cli.cubaplugin.di.sdkKodein
import com.haulmont.cuba.cli.plugin.sdk.dto.Authentication
import com.haulmont.cuba.cli.plugin.sdk.dto.Repository
import com.haulmont.cuba.cli.plugin.sdk.dto.RepositoryTarget
import com.haulmont.cuba.cli.plugin.sdk.dto.RepositoryType
import org.kodein.di.generic.instance
import org.redundent.kotlin.xml.Node
import org.redundent.kotlin.xml.xml
import java.io.FileInputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption

class RepositoryManagerImpl : RepositoryManager {

    internal val sdkSettings: SdkSettingsHolder by sdkKodein.instance()

    inline fun <reified T> fromJson(json: String): T {
        return Gson().fromJson(json, object : TypeToken<T>() {}.type)
    }

    private fun sdkRepositoriesPath() = Path.of(sdkSettings["sdk.repositories"])
    private fun mvnSettingsPath() = Path.of(sdkSettings["mvn.settings"])

    override fun getRepositoryId(target: RepositoryTarget, name: String): String {
        return target.getId() + "." + name.replace("\\s{2,}", " ").toLowerCase()
    }

    private val sdkRepositories by lazy {
        if (Files.exists(sdkRepositoriesPath())) {
            FileInputStream(sdkRepositoriesPath().toString())
                .bufferedReader(StandardCharsets.UTF_8)
                .use {
                    return@lazy fromJson(it.readText()) as LinkedTreeMap<RepositoryTarget, MutableList<Repository>>
                }
        } else {
            return@lazy defaultRepositories()
        }
    }

    private fun defaultRepositories(): Map<RepositoryTarget, MutableList<Repository>> {
        return mapOf(
            RepositoryTarget.SEARCH to mutableListOf(
                Repository(
                    name = "local",
                    type = RepositoryType.LOCAL,
                    url = "file:///" + Paths.get(System.getProperty("user.home")).resolve(".m2").resolve("repository").toString()
                ),
                Repository(
                    name = "cuba-bintray",
                    type = RepositoryType.BINTRAY,
                    url = "https://api.bintray.com/search/packages/maven?",
                    repositoryName = "cuba-platform"
                ),
                Repository(
                    name = "cuba-nexus",
                    type = RepositoryType.NEXUS2,
                    url = "https://repo.cuba-platform.com/service/local/lucene/search",
                    authentication = Authentication(login = "cuba", password = "cuba123")
                )
            ),
            RepositoryTarget.SOURCE to mutableListOf(
                Repository(
                    name = "local",
                    type = RepositoryType.LOCAL,
                    url = "file:///" + Paths.get(System.getProperty("user.home")).resolve(".m2").resolve("repository").toString()
                ),
                Repository(
                    name = "cuba-bintray",
                    type = RepositoryType.BINTRAY,
                    url = "https://dl.bintray.com/cuba-platform/main"
                ),
                Repository(
                    name = "cuba-nexus",
                    type = RepositoryType.NEXUS2,
                    url = "https://repo.cuba-platform.com/content/groups/work",
                    authentication = Authentication(login = "cuba", password = "cuba123")
                )
            ),
            RepositoryTarget.TARGET to mutableListOf()
        )
    }

    override fun getRepository(name: String, target: RepositoryTarget): Repository? {
        return getRepositories(target).firstOrNull { getRepositoryId(target, it.name) == getRepositoryId(target, name) }
    }

    override fun addRepository(repository: Repository, target: RepositoryTarget) {
        if (getRepository(repository.name, target) != null) {
            throw IllegalStateException("Repository with name ${repository.name} already exist")
        }
        (sdkRepositories.get(target)
            ?: throw IllegalStateException("Unknown repository target $target")).add(repository)
        flush()
    }

    override fun removeRepository(name: String, target: RepositoryTarget) {
        getInternalRepositories(target).remove(getRepository(name, target))
        flush()
    }

    private fun flush() {
        flushMetadata()
        buildMavenSettingsFile()
    }

    override fun getRepositories(target: RepositoryTarget): MutableList<Repository> {
        return getInternalRepositories(target)
            .filter { it.active }.toMutableList()
    }

    private fun getInternalRepositories(target: RepositoryTarget) =
        (sdkRepositories.get(target) ?: throw IllegalStateException("Unknown repository target $target"))

    fun flushMetadata() {
        writeToFile(sdkRepositoriesPath(), Gson().toJson(sdkRepositories))
    }

    fun writeToFile(file: Path, text: String) {
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

    override fun buildMavenSettingsFile() {
        val settings = xml("settings") {
            "localRepository" {
                -sdkSettings["mvn.local.repo"]
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
                RepositoryTarget.values().forEach { target ->
                    getRepositories(target)
                        .filter { it.authentication != null }
                        .forEach {
                            "server" {
                                "id" { -getRepositoryId(target, it.name) }
                                "username" { -it.authentication!!.login }
                                "password" { -it.authentication!!.password }
                            }
                        }
                }
            }
        }

        writeToFile(mvnSettingsPath(), settings.toString(true))
    }

    override fun mvnSettingFile(): Path {
        return mvnSettingsPath().also {
            if (!Files.exists(it)) {
                buildMavenSettingsFile()
            }
        }
    }

    private fun addRepositories(rootEl: String, elementName: String, target: RepositoryTarget): Node =
        xml(rootEl) {
            getRepositories(target).forEach {
                elementName {
                    "id" { -getRepositoryId(target, it.name) }
                    "name" { -it.name }
                    "url" { -it.url }
                }
            }
        }
}