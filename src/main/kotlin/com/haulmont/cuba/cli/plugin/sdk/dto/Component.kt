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
    val packageName: String,
    val name: String? = null,
    val version: String,
    val type: ComponentType = ComponentType.LIB,
    val classifiers: MutableList<Classifier> = arrayListOf(
        Classifier.pom(),
        Classifier.default(),
        Classifier.sources(),
        Classifier.javadoc(),
        Classifier.client()
    ),
    val components: MutableSet<Component> = HashSet(),
    val dependencies: MutableSet<MvnArtifact> = HashSet()
) {
    override fun toString(): String {
        return name ?: "${packageName}:${name ?: ""}:${version}"
    }
}