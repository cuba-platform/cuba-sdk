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
import com.haulmont.cuba.cli.plugin.sdk.dto.SearchContext

@Parameters(commandDescription = "Install library to SDK")
class InstallLibCommand : BaseInstallCommand() {

    @Parameter(description = "Lib group, name and version <group>:<name>:<version>")
    private var nameVersion: String? = null

    override fun search(): Component? {
        nameVersion?.split(":")?.let {
            if (it.size == 3) {
                componentManager.search(
                    SearchContext(ComponentType.LIB, it[0], it[1], it[2])
                )?.let { return it }

            }
        }
        fail(messages["unknownLib"].format(nameVersion))
    }
}