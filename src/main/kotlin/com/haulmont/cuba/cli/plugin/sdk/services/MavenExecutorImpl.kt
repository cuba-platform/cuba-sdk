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

package com.haulmont.cuba.cli.plugin.sdk.services

import com.haulmont.cuba.cli.cubaplugin.di.sdkKodein
import org.kodein.di.generic.instance
import java.nio.file.Path
import java.util.logging.Logger

class MavenExecutorImpl : MavenExecutor {

    private val log: Logger = Logger.getLogger(MavenExecutorImpl::class.java.name)
    private val sdkSettings: SdkSettingsHolder by sdkKodein.instance()
    private val repositoryManager: RepositoryManager by sdkKodein.instance()

    override fun mvn(profile: String, command: String, commands: List<String>): MavenExecutor.CommandResult {
        val rt = Runtime.getRuntime()
        val settingsFile = repositoryManager.mvnSettingFile()
        val cliCommandsList = ArrayList(
            arrayOf(
                sdkSettings.sdkHome.resolve(
                    Path.of(
                        sdkSettings["mvn-install-path"],
                        "bin",
                        "mvn.cmd"
                    )
                ).toString(),
                command,
                "-s",
                "\"$settingsFile\"",
                "-P $profile"
            ).asList()
        )
        cliCommandsList.addAll(commands)

        val cliCommands = arrayOfNulls<String>(cliCommandsList.size)
        cliCommandsList.toArray(cliCommands)

        log.fine("Execute maven:${cliCommands.joinToString(separator = " ")}")
        val proc = rt.exec(cliCommands)

        return MavenExecutor.CommandResult(proc.inputStream, proc.errorStream)
    }
}