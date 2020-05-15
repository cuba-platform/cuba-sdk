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
import com.haulmont.cuba.cli.green
import com.haulmont.cuba.cli.plugin.sdk.commands.AbstractSdkCommand
import com.haulmont.cuba.cli.plugin.sdk.di.sdkKodein
import com.haulmont.cuba.cli.plugin.sdk.dto.Authentication
import com.haulmont.cuba.cli.plugin.sdk.dto.Repository
import com.haulmont.cuba.cli.plugin.sdk.dto.RepositoryTarget
import com.haulmont.cuba.cli.plugin.sdk.dto.RepositoryType
import com.haulmont.cuba.cli.plugin.sdk.services.RepositoryManager
import com.haulmont.cuba.cli.prompting.Prompts
import org.kodein.di.generic.instance

@Parameters(commandDescription = "Set license key for SDK")
class LicenseCommand : AbstractSdkCommand() {

    internal val repositoryManager: RepositoryManager by sdkKodein.instance<RepositoryManager>()

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
        licenseKey.split("-").also {
            val login = it[0]
            val password = it[1]

            repositoryManager.removeRepository("cuba-bintray-premium",RepositoryTarget.SEARCH)
            repositoryManager.addRepository(
                Repository(
                    name = "cuba-bintray-premium",
                    type = RepositoryType.BINTRAY,
                    url = "https://api.bintray.com/search/packages/maven?",
                    authentication = Authentication("$login@cuba-platform", password),
                    repositoryName = "cuba-platform"
                ), RepositoryTarget.SEARCH
            )
            repositoryManager.removeRepository("cuba-nexus-premium",RepositoryTarget.SEARCH)
            repositoryManager.addRepository(
                Repository(
                    name = "cuba-nexus-premium",
                    type = RepositoryType.NEXUS2,
                    url = "https://repo.cuba-platform.com/service/local/lucene/search",
                    authentication = Authentication(login, password)
                ), RepositoryTarget.SEARCH
            )

            repositoryManager.removeRepository("cuba-bintray-premium",RepositoryTarget.SOURCE)
            repositoryManager.addRepository(
                Repository(
                    name = "cuba-bintray-premium",
                    type = RepositoryType.BINTRAY,
                    url = "https://cuba-platform.bintray.com/premium",
                    authentication = Authentication("$login@cuba-platform", password)
                ), RepositoryTarget.SOURCE
            )

            repositoryManager.removeRepository("cuba-nexus-premium",RepositoryTarget.SOURCE)
            repositoryManager.addRepository(
                Repository(
                    name = "cuba-nexus-premium",
                    type = RepositoryType.NEXUS2,
                    url = "https://repo.cuba-platform.com/content/groups/premium",
                    authentication = Authentication(login, password)
                ), RepositoryTarget.SOURCE
            )
        }
    }
}