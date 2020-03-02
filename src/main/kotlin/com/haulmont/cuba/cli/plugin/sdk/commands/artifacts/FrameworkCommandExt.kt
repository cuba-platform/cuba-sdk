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
import com.haulmont.cuba.cli.prompting.Option

fun BaseComponentCommand.askResolvedFrameworkNameVersion(nameVersion: NameVersion?): NameVersion {
    val addons = metadataHolder.getResolved()
        .filter { ComponentType.FRAMEWORK == it.type }
    return askNameVersion(
        nameVersion,
        "framework",
        addons.map { it.name }
            .filterNotNull()
            .distinct()
            .sorted()
            .toList()) { name ->
        addons.filter { it.name == name }
            .map { it.version }
            .distinct()
            .sortedDescending()
            .map { Option(it, null, it) }
            .toList()
    }
}

fun BaseComponentCommand.askAllFrameworkNameVersion(nameVersion: NameVersion?): NameVersion = askNameVersion(
    nameVersion,
    "framework",
    listOf("cuba")
) {
    platformVersionsManager.versions.sortedDescending().map { Option(it, null, it) }
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