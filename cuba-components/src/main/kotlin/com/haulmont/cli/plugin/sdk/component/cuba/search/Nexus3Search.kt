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

package com.haulmont.cli.plugin.sdk.component.cuba.search

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.haulmont.cli.plugin.sdk.component.cuba.dto.CubaComponent
import com.haulmont.cli.plugin.sdk.component.cuba.dto.JmixComponent
import com.haulmont.cuba.cli.plugin.sdk.di.sdkKodein
import com.haulmont.cuba.cli.plugin.sdk.dto.Classifier
import com.haulmont.cuba.cli.plugin.sdk.dto.Component
import com.haulmont.cuba.cli.plugin.sdk.dto.Repository
import com.haulmont.cuba.cli.plugin.sdk.services.SdkSettingsHolder
import org.kodein.di.generic.instance

class Nexus3Search(repository: Repository) : AbstractRepositorySearch(repository) {

    private val sdkSettings: SdkSettingsHolder by sdkKodein.instance<SdkSettingsHolder>()

    val cubaNexus3RepoUrl : String by lazy { sdkSettings["cuba.nexus3.repo.url"] }

    override fun searchParameters(component: Component, searchUrl: String): List<Pair<String, String>> {

        return listOf(
            "group" to
                    if (!searchUrl.equals(cubaNexus3RepoUrl) && component.groupId.equals("io.jmix")) {
                        component.groupId + "*"
                    } else {
                        component.groupId
                    },
            "name" to
                    if (!searchUrl.equals(cubaNexus3RepoUrl))
                        "jmix-*"
                    else
                        ((component as CubaComponent).globalModule()?.artifactId?.substringBefore("-global")
                            ?: "") + "*",
            "version" to component.version
        )
    }

    override fun handleResultJson(it: JsonElement, component: Component): Component? {
        val json = it as JsonObject
        val itemsArray = json.getAsJsonArray("items")
        if (itemsArray.size() == 0) {
            log.info("Unknown version: ${component.version}")
            return null
        }
        itemsArray.map { it as JsonObject }
            .map { dataObj ->
                val groupId = dataObj.get("group").asString
                val artifactId = dataObj.get("name").asString
                if (repository.url.equals(cubaNexus3RepoUrl)) {
                    (component as CubaComponent).globalModule()?.let {
                        val prefix = it.artifactId.substringBefore("-global")
                        if (!artifactId.startsWith(prefix)) {
                            return@map null
                        }
                    }
                }
                val version = dataObj.get("version").asString
                val classifiers = dataObj.getAsJsonArray("assets")
                    .map { it as JsonObject }
                    .map { asset ->
                        val path = asset.get("path").asString
                        val classifierAndExtension = path.substringAfterLast("${groupId}-${version}")
                        val classifier = if (classifierAndExtension.isNotEmpty())
                            classifierAndExtension.substringAfter("-").substringBefore(".") else ""
                        val extension = classifierAndExtension.substringAfter(".")
                        Classifier(classifier, extension)
                    }
                    .toMutableSet()
                return@map Component(groupId, artifactId, version, classifiers = classifiers)
            }
            .filterNotNull()
            .forEach {
                if (componentAlreadyExists(component.components, it) == null) {
                    component.components.add(it)
                }
            }
        log.fine("Component found in ${repository}: $component")
        return component
    }
}