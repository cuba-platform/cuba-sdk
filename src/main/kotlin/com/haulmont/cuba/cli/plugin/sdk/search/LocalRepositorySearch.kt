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

package com.haulmont.cuba.cli.plugin.sdk.search

import com.haulmont.cuba.cli.plugin.sdk.dto.Classifier
import com.haulmont.cuba.cli.plugin.sdk.dto.Component
import com.haulmont.cuba.cli.plugin.sdk.dto.Repository
import java.nio.file.Files
import java.nio.file.Path
import java.util.logging.Logger

class LocalRepositorySearch : RepositorySearch {

    internal val log: Logger = Logger.getLogger(BintraySearch::class.java.name)
    internal val repository: Repository

    constructor(repository: Repository) {
        this.repository = repository
    }

    override fun search(component: Component): Component? {
        var baseSearchPath: Path = Path.of(repository.url.substringAfter("file:///"))
        for (groupPart in component.packageName.split(".")) {
            baseSearchPath = baseSearchPath.resolve(groupPart)
        }
        if (!Files.exists(baseSearchPath)) {
            return null
        }
        val baseDir = baseSearchPath.toFile()
        val componentsList = mutableListOf<Component>()
        if (baseDir.listFiles() != null) {
            baseDir.listFiles()
                .filter { it.isDirectory }
                .forEach { componentDir ->
                    val componentName = componentDir.name
                    val componentPrefix = "$componentName-${component.version}"
                    componentDir.listFiles()
                        .filter { it.isDirectory && it.name == component.version }
                        .forEach {
                            componentsList.add(Component(packageName = component.packageName,
                                name = componentName,
                                version = component.version,
                                classifiers = it.listFiles()
                                    .filter { it.name.startsWith(componentPrefix) }
                                    .map { artifactFile ->
                                        val split = artifactFile.name.substringAfter(componentPrefix).split(".")
                                        Classifier(split.get(0).substringAfter("-"), split.get(1))
                                    }
                                    .toMutableList()))
                        }

                }
        }

        if (componentsList.isNotEmpty()) {
            val copy = component.copy()
            copy.components.clear()
            copy.components.addAll(componentsList)
            return copy
        }
        return null
    }
}