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

import com.haulmont.cuba.cli.plugin.sdk.dto.Component
import com.haulmont.cuba.cli.plugin.sdk.dto.MvnArtifact
import com.haulmont.cuba.cli.plugin.sdk.dto.Repository

typealias ResolveProgressCallback = (component: Component, resolved: Float, total: Int) -> Unit
typealias UploadProcessCallback = (artifact: MvnArtifact, uploaded: Int, total: Int) -> Unit
typealias RemoveProcessCallback = (artifact: MvnArtifact, removed: Int, total: Int) -> Unit

interface ComponentManager {

    fun isAlreadyInstalled(component: Component): Boolean

    fun searchInMetadata(component: Component): Component?

    fun resolve(component: Component, progress: ResolveProgressCallback? = null): Component?

    fun searchForAdditionalComponents(component: Component): Set<Component>

    fun upload(component: Component, repositories: List<Repository>, isImported: Boolean = false,  progress: UploadProcessCallback? = null)

    fun remove(componentToRemove: Component, removeFromRepo: Boolean, progress: RemoveProcessCallback? = null)

    fun register(component: Component)
}