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

package com.haulmont.cuba.cli.plugin.sdk.commands.repository

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import com.haulmont.cuba.cli.cubaplugin.di.sdkKodein
import com.haulmont.cuba.cli.green
import com.haulmont.cuba.cli.plugin.sdk.commands.AbstractSdkCommand
import com.haulmont.cuba.cli.plugin.sdk.services.RepositoryManager
import com.haulmont.cuba.cli.prompting.Prompts
import org.kodein.di.generic.instance

@Parameters(commandDescription = "Set license key for SDK")
class LicenseCommand : AbstractSdkCommand() {

    internal val repositoryManager: RepositoryManager by sdkKodein.instance()

    @Parameter(
        description = "License key in format ************-************",
        hidden = true
    )
    private var licenseKey: String? = null

    override fun run() {
        if (licenseKey == null) {
            licenseKey = Prompts.create {
                question("licenseKey", messages["license.askLicenseCaption"]) {
                    validate {
                        checkRegex(this.value)
                    }
                }
            }.ask()["licenseKey"] as String
        }

        if (licenseKey != null) {
            checkRegex(licenseKey!!)
            activate(licenseKey!!)

            printWriter.println(messages["license.licenseKeyconfigured"].green())
        }
    }

    private fun checkRegex(licenseKey: String) {
        if (!Regex("[0-9a-zA-Z]+-[0-9a-zA-Z]+").matches(licenseKey))
            fail("License key should be form of ************-************")
    }

    private fun activate(licenseKey: String) {
        repositoryManager.addPremiumRepository(licenseKey)
    }
}