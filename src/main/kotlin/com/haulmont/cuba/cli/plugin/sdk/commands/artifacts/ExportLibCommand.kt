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

package com.haulmont.cuba.cli.plugin.sdk.commands.artifacts

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import com.haulmont.cuba.cli.cubaplugin.di.sdkKodein
import com.haulmont.cuba.cli.plugin.sdk.dto.Component
import com.haulmont.cuba.cli.plugin.sdk.dto.MvnArtifact
import com.haulmont.cuba.cli.plugin.sdk.services.ArtifactManager
import org.kodein.di.generic.instance

@Parameters(commandDescription = "Export library with dependencies")
class ExportLibCommand : AbstractComponentExportCommand() {

    internal val artifactManager: ArtifactManager by sdkKodein.instance()

    @Parameter(description = "Lib group, name and version <group>:<name>:<version>")
    private var nameVersion: String? = null

    override fun createSearchContext(): Component? {
        return nameVersion?.resolveLibraryCoordinates() ?: fail(messages["lib.unknown"].format(nameVersion))
    }

    override fun search(component: Component): Component? {
        artifactManager.readPom(MvnArtifact(component.packageName, component.name as String, component.version))
            ?: return null
        return component
    }
}