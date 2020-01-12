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

package com.haulmont.cuba.cli.plugin.sdk.commands

import com.beust.jcommander.Parameter
import com.haulmont.cuba.cli.commands.AbstractCommand
import com.haulmont.cuba.cli.cubaplugin.di.sdkKodein
import com.haulmont.cuba.cli.green
import com.haulmont.cuba.cli.localMessages
import com.haulmont.cuba.cli.plugin.sdk.services.SdkSettingsHolder
import org.kodein.di.generic.instance
import java.io.PrintWriter
import java.nio.file.Path

abstract class AbstractSdkCommand : AbstractCommand() {

    internal val PROGRESS_LINE_LENGHT = 110

    internal val messages by localMessages()
    internal val printWriter: PrintWriter by sdkKodein.instance()
    internal val sdkSettings: SdkSettingsHolder by sdkKodein.instance()

    @Parameter(
        names = ["-s", "--settings"],
        description = "Settings file",
        hidden = true
    )
    internal var settingsFile: String? = null

    @Parameter(
        names = ["-p", "--parameter"],
        description = "Settings parameter",
        hidden = true,
        variableArity = true
    )
    internal var parameters: List<String>? = null

    override fun preExecute() {
        super.preExecute()
        if (settingsFile != null) {
            sdkSettings.setExternalProperties(Path.of(settingsFile))
        }
        parameters?.let {
            it.forEach {
                it.split("=").also {
                    sdkSettings[it[0]] = it[1]
                }
            }
        }
    }

    override fun postExecute() {
        super.postExecute()
        if (settingsFile != null || parameters != null) {
            sdkSettings.resetProperties()
        }
    }

    internal fun printProgress(message: String, progress: Float): String {
        var progressStr = messages["progress"].format(progress).green()
        if (progress == 100f) {
            progressStr += "\n"
        }
        val maxLength = PROGRESS_LINE_LENGHT - progressStr.length
        return "\r" + message.substring(IntRange(0, maxLength - 1)).padEnd(maxLength) + progressStr;
    }
}