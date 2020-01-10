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
import com.haulmont.cuba.cli.plugin.sdk.commands.CommonSdkParameters
import org.kodein.di.generic.instance
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.PrintWriter
import java.nio.file.Path
import java.util.logging.Logger

class MavenExecutorImpl : MavenExecutor {

    private val log: Logger = Logger.getLogger(MavenExecutorImpl::class.java.name)
    private val sdkSettings: SdkSettingsHolder by sdkKodein.instance()
    private val repositoryManager: RepositoryManager by sdkKodein.instance()
    private val printWriter: PrintWriter by sdkKodein.instance()

    override fun mvn(profile: String, command: String, commands: List<String>): String {
        val rt = Runtime.getRuntime()
        val settingsFile = repositoryManager.mvnSettingFile()
        val cliCommandsList = ArrayList(
            arrayOf(
                Path.of(
                    sdkSettings["mvn.path"],
                    "bin",
                    "mvn.cmd"
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

        val commandOutput = StringBuilder()
        if (CommonSdkParameters.printMaven) {
            printWriter.println()
        }
        proc.inputStream.readMvn {
            if (it != null) {
                val output = it.mvnFormatOutput()
                if (CommonSdkParameters.printMaven) {
                    printWriter.print(output)
                }
                commandOutput.append(output)
            }
        }
        if (CommonSdkParameters.printMaven) {
            printWriter.println()
        }
        return commandOutput.toString()
    }

    fun InputStream.readMvn(handleResult: (m: String?) -> Unit) {
        InputStreamReader(this).use {
            BufferedReader(it).use {
                var s: String?
                while (it.readLine().also { s = it } != null) {
                    handleResult(s)
                }
            }
        }
    }

    fun String.mvnFormatOutput(): String? {
        val s = if (this.startsWith("Progress ")) "\r" + this else this + "\n"
        Logger.getLogger(MavenExecutorImpl::class.java.name).fine("mvn:$s")
        return s
    }

}