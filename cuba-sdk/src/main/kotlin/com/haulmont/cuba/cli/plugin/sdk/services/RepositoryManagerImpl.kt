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
import com.google.common.eventbus.EventBus
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.haulmont.cli.core.kodein
import com.haulmont.cli.core.prompting.ValidationException
import com.haulmont.cuba.cli.plugin.sdk.di.sdkKodein
import com.haulmont.cuba.cli.plugin.sdk.dto.Authentication
import com.haulmont.cuba.cli.plugin.sdk.dto.Repository
import com.haulmont.cuba.cli.plugin.sdk.dto.RepositoryTarget
import com.haulmont.cuba.cli.plugin.sdk.dto.RepositoryType
import com.haulmont.cuba.cli.plugin.sdk.event.SdkEvent
import com.haulmont.cuba.cli.plugin.sdk.utils.authorizeIfRequired
import org.kodein.di.generic.instance
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class RepositoryManagerImpl : RepositoryManager {

    internal val sdkSettings: SdkSettingsHolder by sdkKodein.instance<SdkSettingsHolder>()
    internal val dbProvider: DbProvider by sdkKodein.instance<DbProvider>()
    private val bus: EventBus by kodein.instance<EventBus>()

    inline fun <reified T> fromJson(json: String): T {
        return Gson().fromJson(json, object : TypeToken<T>() {}.type)
    }

    override fun getRepositoryId(target: RepositoryTarget, name: String): String {
        return target.getId() + "." + name.replace("\\s{2,}", " ").toLowerCase()
    }

    private val sdkRepositories by lazy {
        if (dbProvider.dbExists("repository")) {
            val repos = HashMap<RepositoryTarget, MutableList<Repository>>()
            for (target in RepositoryTarget.values()) {
                repos.putIfAbsent(target, mutableListOf())
                dbInstance().map(target.toString()).forEach {
                    val json = it.value
                    if (json != null) {
                        val repository = fromJson(json) as Repository
                        repos.get(target)?.add(repository)
                    }
                }
            }
            return@lazy repos
        } else {
            val repos = defaultRepositories()
            for (target in RepositoryTarget.values()) {
                repos[target]?.forEach {
                    dbInstance().set(target.toString(), it.name, Gson().toJson(it))
                }
            }
            return@lazy repos
        }
    }

    private fun defaultRepositories(): Map<RepositoryTarget, MutableList<Repository>> {
        return mapOf(
            RepositoryTarget.SOURCE to mutableListOf(
                Repository(
                    name = "local",
                    type = RepositoryType.LOCAL,
                    url = Paths.get(System.getProperty("user.home")).resolve(".m2").resolve("repository").toString()
                ),
                Repository(
                    name = "central",
                    type = RepositoryType.NEXUS2,
                    url = "https://repo1.maven.org/maven2/"
                ),
//                Repository(
//                    name = "cuba-bintray",
//                    type = RepositoryType.BINTRAY,
//                    url = "https://dl.bintray.com/cuba-platform/main"
//                ),
                Repository(
                    name = "cuba-nexus",
                    type = RepositoryType.NEXUS2,
                    url = "https://repo.cuba-platform.com/content/groups/work",
                    authentication = Authentication(login = "cuba", password = "cuba123")
                ),
                Repository(
                    name = "cuba",
                    type = RepositoryType.NEXUS3,
                    url = "https://nexus.cuba-platform.cn/repository/cuba/"
                ),
                Repository(
                    name = "public",
                    type = RepositoryType.NEXUS3,
                    url = "https://nexus.jmix.io/repository/public/"
                ),
                Repository(
                    name = "public",
                    type = RepositoryType.NEXUS3,
                    url = "https://global.repo.jmix.io/repository/public/"
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
        bus.post(SdkEvent.BeforeAddRepositoryEvent(repository))
        if (RepositoryType.LOCAL == repository.type && !Files.exists(Path.of(repository.url))) {
            Files.createDirectories(Path.of(repository.url))
        }
        dbInstance().set(target.toString(), repository.name, Gson().toJson(repository))
        (sdkRepositories[target]
            ?: throw IllegalStateException("Unknown repository target $target")).add(repository)
        bus.post(SdkEvent.AfterAddRepositoryEvent(repository))
    }

    override fun removeRepository(name: String, target: RepositoryTarget, force: Boolean) {
        if (!force && RepositoryTarget.TARGET == target
            && name == sdkSettings.getIfExists("repository.name")
        ) {
            throw ValidationException("Unable to delete configured local SDK repository")
        }

        val repository = getRepository(name, target)
        if (repository != null) {
            bus.post(SdkEvent.AfterRemoveRepositoryEvent(repository))
            getInternalRepositories(target).remove(repository)
            dbInstance().remove(target.toString(), name)
            bus.post(SdkEvent.AfterRemoveRepositoryEvent(repository))
        }
    }

    private fun dbInstance() = dbProvider.get("repository")

    override fun getRepositories(target: RepositoryTarget): MutableList<Repository> {
        return getInternalRepositories(target)
            .filter { it.active }.toMutableList()
    }

    override fun isOnline(repository: Repository): Boolean {
        var url = repository.url
        if (RepositoryType.LOCAL == repository.type) {
            return Files.exists(Path.of(url))
        } else {
            if (sdkSettings.getIfExists("repository.name") == repository.name) {
                url = sdkSettings["repository.url"]
            }
            val (_, response, _) =
                Fuel.head(url)
                    .authorizeIfRequired(repository)
                    .response()
            return response.statusCode == 200 || response.statusCode == 403
        }
    }

    private fun getInternalRepositories(target: RepositoryTarget) =
        (sdkRepositories.get(target) ?: throw IllegalStateException("Unknown repository target $target"))


    override fun getLocalRepositoryPassword(): String? {
        val repo = getRepository(sdkSettings["repository.name"], RepositoryTarget.TARGET)
        if (repo?.authentication != null) {
            return repo.authentication.password
        }
        return null
    }
}