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

package com.haulmont.cli.plugin.sdk.component.cuba.dto

import com.haulmont.cuba.cli.plugin.sdk.dto.Classifier
import com.haulmont.cuba.cli.plugin.sdk.dto.Component
import com.haulmont.cuba.cli.plugin.sdk.dto.MvnArtifact

class CubaComponent(
    groupId: String,
    artifactId: String,
    version: String,
    classifiers: MutableList<Classifier> = mutableListOf(
        Classifier.default(),
        Classifier.pom(),
        Classifier.sources()
    ),

    id: String? = null,
    type: String = "",
    name: String? = null,
    description: String? = null,
    category: String? = null,
    var frameworkVersion: String? = null,

    url: String? = null,

    components: MutableSet<Component> = HashSet(),
    dependencies: MutableSet<MvnArtifact> = HashSet()
) : Component(
    groupId,
    artifactId,
    version,
    classifiers,

    id,
    type,
    name,
    description,
    category,

    url,

    components,
    dependencies
) {
    fun globalModule() =
        components.firstOrNull { it.artifactId.endsWith("-global") }
}