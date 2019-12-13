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
import com.haulmont.cuba.cli.plugin.sdk.dto.Component
import com.haulmont.cuba.cli.plugin.sdk.dto.ComponentType

@Parameters(commandDescription = "Install framework to SDK")
class InstallFrameworkCommand : BaseInstallCommand() {

    @Parameter(description = "Framework name and version <name>:<version>")
    private var frameworkNameVersion: String? = null

    override fun createSearchContext(): Component? {
        frameworkNameVersion?.split(":")?.let {
            if (it.size == 2) {
                return Component(packageName = it[0], version = it[1], type = ComponentType.FRAMEWORK)
            }
        }
        fail(messages["unknownFramework"].format(frameworkNameVersion))
    }


}