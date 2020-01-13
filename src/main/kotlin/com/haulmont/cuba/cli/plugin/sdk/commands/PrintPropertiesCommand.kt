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

package com.haulmont.cuba.cli.plugin.sdk.commands

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import com.haulmont.cuba.cli.green
import com.haulmont.cuba.cli.plugin.sdk.utils.doubleUnderline

@Parameters(commandDescription = "Print SDK properties")
class PrintPropertiesCommand : AbstractSdkCommand() {

    @Parameter(
        names = ["--n", "--name"],
        description = "Parameter name",
        hidden = true,
        variableArity = true
    )
    private var names: List<String>? = null

    override fun run() {
        printWriter.println(messages["sdk.properties"].doubleUnderline())
        for (property in propertyNames().sorted()) {
            printWriter.println("$property: ${sdkSettings[property].green()}")
        }
    }

    private fun propertyNames(): Collection<String> = names ?: sdkSettings.propertyNames()

    override fun onlyForConfiguredSdk(): Boolean  = false
}