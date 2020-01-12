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

package com.haulmont.cuba.cli.plugin.sdk.commands.artifacts

import com.haulmont.cuba.cli.plugin.sdk.dto.Component
import com.haulmont.cuba.cli.plugin.sdk.dto.ComponentType

fun BaseComponentCommand.askResolvedFrameworkNameVersion(): NameVersion {
    val addons = metadataHolder.getMetadata().components
        .filter { ComponentType.FRAMEWORK == it.type }
    return askNameVersion(
        "framework",
        addons.map { it.name }
            .requireNoNulls()
            .distinct()
            .toList()) { name ->
        addons.filter { it.name == name }
            .map { it.version }
            .distinct()
            .toList()
    }
}

fun BaseComponentCommand.askAllFrameworkNameVersion(): NameVersion = askNameVersion(
    "framework",
    listOf("cuba")
) {
    platformVersionsManager.versions
}


fun String.resolveFrameworkCoordinates(): Component? {
    this.split(":").let {
        when (it.size) {
            2 -> return Component(
                packageName = it[0],
                version = it[1],
                type = ComponentType.FRAMEWORK
            )
            else -> return null
        }
    }
}