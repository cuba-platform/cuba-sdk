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

@Parameters(commandDescription = "Install framework to SDK")
class InstallFrameworkCommand : BaseInstallCommand() {

    @Parameter(description = "Framework name and version <name>:<version>", hidden = true)
    private var nameVersion: String? = null

    override fun run() {
        nameVersion = askAllFrameworkNameVersion(nameVersion)
        super.run()
    }

    override fun createSearchContext(): Component? {
        return nameVersion?.resolveFrameworkCoordinates() ?: fail(
            messages["unknownFramework"].format(
                nameVersion
            )
        )
    }


}