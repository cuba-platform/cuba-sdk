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
import com.haulmont.cuba.cli.Messages
import com.haulmont.cuba.cli.WorkingDirectoryManager
import com.haulmont.cuba.cli.commands.AbstractCommand
import com.haulmont.cuba.cli.cubaplugin.di.sdkKodein
import com.haulmont.cuba.cli.green
import com.haulmont.cuba.cli.localMessages
import com.haulmont.cuba.cli.plugin.sdk.perf.SdkPerformance
import com.haulmont.cuba.cli.plugin.sdk.services.SdkSettingsHolder
import com.haulmont.cuba.cli.prompting.ValidationException
import org.kodein.di.generic.instance
import java.io.PrintWriter
import java.nio.file.Path

abstract class AbstractSdkCommand : AbstractCommand() {

    companion object{
        internal val PROGRESS_LINE_LENGHT = 110
        internal val rootMessages = Messages(AbstractSdkCommand::class.java)
        internal val printWriter: PrintWriter by sdkKodein.instance()

        fun printProgress(message: String, progress: Float) {
            val progressStr = rootMessages["progress"].format(progress).green()
            val maxLength = PROGRESS_LINE_LENGHT - progressStr.length

            val trim = if (message.length > maxLength - 1) message.substring(IntRange(0, maxLength - 1)) else message
            printWriter.print("\r" + trim.padEnd(maxLength) + progressStr)
            if (progress == 100f) {
                printWriter.println()
            }
        }

        fun calculateProgress(count: Int, total: Int) = calculateProgress(count.toFloat(), total)

        fun calculateProgress(count: Long, total: Long) = calculateProgress(count.toFloat(), total.toFloat())

        fun calculateProgress(count: Float, total: Int) = calculateProgress(count, total.toFloat())

        fun calculateProgress(count: Float, total: Float) = count / total * 100

        internal fun printProgressMessage(msg: String, periodMs: Long = 100, i: Int = 0) {
            val padLength = 10
            if (i % padLength == 0) {
                printWriter.print("\r$msg".padEnd(msg.length + padLength))
            }
            waitAndPrintProgress(periodMs, msg.padEnd(msg.length + i % padLength, '.'))
        }

        internal fun waitAndPrintProgress(period: Long, msg: String) {
            Thread.sleep(period)
            printWriter.print("\r$msg")
        }

        fun waitTask(msg: String, periodMs: Long = 100, waitConditionFun: () -> Boolean) {
            printProgressMessage(msg)
            var i = 0
            while (waitConditionFun()) {
                printProgressMessage(msg, periodMs, i++)
            }
            printWriter.println()
        }
    }

    internal val messages by localMessages()
    internal val sdkSettings: SdkSettingsHolder by sdkKodein.instance()
    internal val workingDirectoryManager: WorkingDirectoryManager by sdkKodein.instance()

    @Parameter(
        names = ["--s", "--settings"],
        description = "Settings file",
        hidden = true
    )
    internal var settingsFile: String? = null

    @Parameter(
        names = ["--sp", "--setting-property"],
        description = "Settings parameter",
        hidden = true,
        variableArity = true
    )
    internal var parameters: List<String>? = null

    @Parameter(
        names = ["--performance"],
        description = "Measure task performance",
        hidden = true
    )
    var performance: Boolean = false
        private set

    override fun preExecute() {
        super.preExecute()
        CommonSdkParameters.measurePerformance = performance
        SdkPerformance.init(this.javaClass.name)
        if (settingsFile != null) {
            var file = Path.of(settingsFile)
            if (!file.isAbsolute) {
                file = workingDirectoryManager.workingDirectory.resolve(file)
            }
            sdkSettings.setExternalProperties(file)
        }
        parameters?.let {
            it.forEach {
                it.split("=").also {
                    sdkSettings[it[0]] = it[1]
                }
            }
        }
        if (onlyForConfiguredSdk() && !sdkSettings.sdkConfigured()) {
            throw ValidationException(rootMessages["sdk.notConfigured"])
        }
    }

    override fun postExecute() {
        super.postExecute()
        SdkPerformance.finish()
        if (settingsFile != null || parameters != null) {
            sdkSettings.resetProperties()
        }
    }



    internal fun password(password: String) = "*****"

    open fun onlyForConfiguredSdk(): Boolean = true
}