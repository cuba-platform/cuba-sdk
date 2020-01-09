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
import com.haulmont.cuba.cli.plugin.sdk.dto.ComponentType
import com.haulmont.cuba.cli.plugin.sdk.services.MetadataHolder
import com.haulmont.cuba.cli.prompting.Prompts
import org.kodein.di.generic.instance

@Parameters(commandDescription = "Upload framework with dependencies to SDK target repository")
class PushFrameworkCommand : BasePushCommand() {

    private val metadataHolder: MetadataHolder by sdkKodein.instance()

    @Parameter(description = "Framework name and version <name>:<version>", hidden = true)
    private var frameworkNameVersion: String? = null

    override fun run() {
        if (frameworkNameVersion == null) {
            val addonAnswers = Prompts.create {
                textOptions("name", messages["framework.name"], metadataHolder.getMetadata().components
                    .filter { ComponentType.FRAMEWORK == it.type }
                    .filter { it.name != null }
                    .map { it.name }
                    .distinct()
                    .toList() as List<String>
                )
            }.ask()

            val name = addonAnswers["name"] as String

            val versionAnswers = Prompts.create {
                textOptions(
                    "version",
                    messages["framework.version"],
                    metadataHolder.getMetadata().components
                        .filter { ComponentType.FRAMEWORK == it.type }
                        .filter { it.name == name }
                        .map { it.version }
                        .distinct()
                        .toList())
            }.ask()
            installAddonCommand(name, versionAnswers["version"] as String)
        } else {
            super.run()
        }
    }

    private fun installAddonCommand(name: String, version: String) {
        frameworkNameVersion = "$name:$version"
        super.run()
    }

    override fun createSearchContext(): Component? {
        return frameworkNameVersion?.resolveFrameworkCoordinates() ?: fail(
            messages["unknownFramework"].format(
                frameworkNameVersion
            )
        )
    }


}