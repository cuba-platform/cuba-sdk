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

fun BaseComponentCommand.askResolvedAddonNameVersion(nameVersion: NameVersion?): NameVersion {
    val addons = metadataHolder.getMetadata().components
        .filter { ComponentType.ADDON == it.type }
    return askNameVersion(
        nameVersion,
        "addon",
        addons.map { it.name }
            .filterNotNull()
            .distinct()
            .sorted()
            .toList()) { addonName ->
        addons.filter { it.name == addonName }
            .distinctBy { it.version }
            .sortedByDescending { it.version }
            .map {
                Option(
                    it.version,
                    rootMessages["framework.cuba.version"].format(it.version, it.frameworkVersion),
                    it.version
                )
            }
            .toList()
    }
}

fun BaseComponentCommand.askAllAddonsNameVersion(nameVersion: NameVersion?): NameVersion {
    val addons = componentVersionsManager.addons()
    return askNameVersion(
        nameVersion,
        "addon",
        addons.map { it.id }.sorted().toList()
    ) { addonName ->
        addons.filter { it.id == addonName || "${it.groupId}.${it.artifactId}" == addonName }
            .flatMap { it.compatibilityList }
            .flatMap {
                it.artifactVersions.map { version ->
                    Option(
                        version,
                        rootMessages["framework.cuba.version"].format(version, it.platformRequirement),
                        version
                    )
                }
            }
            .sortedByDescending { it.value }
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