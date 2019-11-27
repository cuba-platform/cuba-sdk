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

import com.beust.jcommander.Parameters
import com.haulmont.cuba.cli.commands.AbstractCommand
import com.haulmont.cuba.cli.cubaplugin.di.sdkKodein
import com.haulmont.cuba.cli.plugin.sdk.dto.Artifact
import com.haulmont.cuba.cli.plugin.sdk.services.NexusRepositoryManager
import com.haulmont.cuba.cli.plugin.sdk.services.SdkSettingsHolder
import org.kodein.di.generic.instance

@Parameters(commandDescription = "Install framework to SDK")
class InstallFrameworkCommand : AbstractCommand() {

    internal val sdkSettings: SdkSettingsHolder by sdkKodein.instance()

    internal val repositoryManager: NexusRepositoryManager by sdkKodein.instance()

    override fun run() {
        val artifacts = HashSet<Artifact>()
        val version = "7.1.2"
        artifacts.addAll(resolveWithDependencies(Artifact("com.haulmont.cuba", "cuba-global", version)))
        artifacts.addAll(resolveWithDependencies(Artifact("com.haulmont.cuba", "cuba-core", version)))
        artifacts.addAll(resolveWithDependencies(Artifact("com.haulmont.cuba", "cuba-client", version)))
        artifacts.addAll(resolveWithDependencies(Artifact("com.haulmont.cuba", "cuba-web", version)))
        artifacts.addAll(resolveWithDependencies(Artifact("com.haulmont.cuba", "cuba-gui", version)))
        artifacts.addAll(resolveWithDependencies(Artifact("com.haulmont.cuba", "cuba-web-toolkit", version)))
        artifacts.addAll(resolveWithDependencies(Artifact("com.haulmont.cuba", "cuba-web-widgets", version)))

        val s=""
    }

    private fun resolveWithDependencies(artifact: Artifact): HashSet<Artifact> {
        val dependencies = HashSet<Artifact>()
        dependencies.add(artifact)
        dependencies.addAll(repositoryManager.findDependencies(artifact))
        return dependencies
    }
}