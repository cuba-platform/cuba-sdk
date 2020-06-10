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
import com.haulmont.cuba.cli.plugin.sdk.dto.Classifier
import com.haulmont.cuba.cli.plugin.sdk.dto.Component
import com.haulmont.cuba.cli.plugin.sdk.dto.Repository
import java.nio.file.Files
import java.nio.file.Path

class LocalRepositorySearch(repository: Repository) : AbstractRepositorySearch(repository) {
    override fun searchParameters(component: Component): List<Pair<String, String>> {
        return emptyList()
    }

    override fun search(component: Component): Component? {
        var baseSearchPath: Path = Path.of(repository.url)
        for (groupPart in component.groupId.split(".")) {
            baseSearchPath = baseSearchPath.resolve(groupPart)
        }
        if (!Files.exists(baseSearchPath)) {
            return null
        }
        val baseDir = baseSearchPath.toFile()
        val componentsList = mutableListOf<Component>()
        baseDir.listFiles()?.let { list ->
            list.filter { it.isDirectory }
                .forEach { componentDir ->
                    val componentName = componentDir.name
                    val componentPrefix = "$componentName-${component.version}"
                    componentDir.listFiles()
                        .filter { it.isDirectory && it.name == component.version }
                        .forEach {
                            val componentToResolve = Component(
                                groupId = component.groupId,
                                artifactId = componentName,
                                version = component.version,
                                classifiers = it.listFiles()
                                    .filter { it.name.startsWith(componentPrefix) }
                                    .map { artifactFile ->
                                        val split = artifactFile.name.substringAfter(componentPrefix).split(".")
                                        Classifier(split.get(0).substringAfter("-"), split.get(1))
                                    }
                                    .toMutableList())
                            if (componentAlreadyExists(component.components, componentToResolve) == null) {
                                component.components.add(componentToResolve)
                            }
                        }

                }
        }

        if (componentsList.isNotEmpty()) {
            return component
        }
        return null
    }

    override fun handleResultJson(it: JsonElement, component: Component): Component? {
        return null
    }
}