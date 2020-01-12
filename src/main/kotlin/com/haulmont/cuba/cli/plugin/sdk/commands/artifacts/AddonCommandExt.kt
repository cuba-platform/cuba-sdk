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

fun BaseComponentCommand.askResolvedAddonNameVersion(nameVersion: NameVersion?): NameVersion {
    val addons = metadataHolder.getMetadata().components
        .filter { ComponentType.ADDON == it.type }
    return askNameVersion(
        nameVersion,
        "addon",
        addons.map { it.name }
            .requireNoNulls()
            .distinct()
            .toList()) { addonName ->
        addons.filter { it.name == addonName }
            .map { it.version }
            .distinct()
            .toList()
    }
}

fun BaseComponentCommand.askAllAddonsNameVersion(nameVersion: NameVersion?): NameVersion {
    val addons = componentVersionsManager.addons()
    return askNameVersion(
        nameVersion,
        "addon",
        addons.map { it.id }.toList()
    ) { addonName ->
        addons.filter { it.id == addonName }
            .flatMap { it.compatibilityList }
            .flatMap { it.artifactVersions }
            .toList()
    }
}

fun String.resolveAddonCoordinates(): Component? {
    this.split(":").let {
        when (it.size) {
            3 -> return Component(
                packageName = it[0],
                name = it[1].substringBefore("-global"),
                version = it[2],
                type = ComponentType.ADDON
            )
            2 -> return Component(
                packageName = it[0],
                version = it[1],
                type = ComponentType.ADDON
            )
            else -> return null
        }
    }
}