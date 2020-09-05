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

import com.google.gson.Gson

open class Component(
    var groupId: String,
    var artifactId: String,
    var version: String,
    var classifiers: MutableSet<Classifier> = mutableSetOf(
        Classifier.jar(),
        Classifier.pom(),
        Classifier.sources()
    ),

    var id: String? = null,
    var type: String = "",
    var name: String? = null,
    var description: String? = null,
    var category: String? = null,

    var url: String? = null,
    var components: MutableSet<Component> = HashSet(),
    var dependencies: MutableSet<MvnArtifact> = HashSet()
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

    fun isSame(component: Component) = type == component.type &&
            artifactId == component.artifactId &&
            groupId == component.groupId &&
            version == component.version

    fun clone(): Component {
        val stringProject = Gson().toJson(this, javaClass)
        return Gson().fromJson(stringProject, javaClass)
    }
}