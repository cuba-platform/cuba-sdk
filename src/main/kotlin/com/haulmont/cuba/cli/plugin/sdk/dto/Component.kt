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

package com.haulmont.cuba.cli.plugin.sdk.dto

data class Component(
    val groupId: String,
    val artifactId: String,
    val version: String,
    val classifiers: MutableList<Classifier> = mutableListOf(
        Classifier.default(),
        Classifier.pom(),
        Classifier.sources()
    ),
    val url: String? = null,
    var frameworkVersion: String? = null,
    val components: MutableSet<Component> = HashSet(),
    val dependencies: MutableSet<MvnArtifact> = HashSet(),
    val type: String = "",
    val id: String? = null,
    val name: String? = null,
    val description: String? = null,
    val category: String? = null
) {
    override fun toString(): String {
        name?.let {
            return "$name:$version"
        }
        id?.let {
            return "$id:$version"
        }
        return "$groupId:$artifactId:$version"
    }

    fun collectAllDependencies(): Set<MvnArtifact> {
        val list = mutableSetOf<MvnArtifact>()
        list.addAll(dependencies)
        components.forEach {
            list.addAll(it.dependencies)
        }
        return list
    }

    fun globalModule() =
        components.filter { it.artifactId.endsWith("-global") }.firstOrNull()

    fun isSame(component: Component) = type == component.type &&
            artifactId == component.artifactId &&
            groupId == component.groupId &&
            version == component.version
}