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

package com.haulmont.cuba.cli.plugin.sdk.perf

import com.haulmont.cuba.cli.plugin.sdk.commands.CommonSdkParameters
import com.haulmont.cuba.cli.plugin.sdk.di.sdkKodein
import com.haulmont.cuba.cli.plugin.sdk.services.SdkSettingsHolder
import org.kodein.di.generic.instance
import java.io.PrintWriter
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.text.NumberFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object SdkPerformance {

    internal val sdkSettings: SdkSettingsHolder by sdkKodein.instance<SdkSettingsHolder>()

    internal val timers = mutableListOf<TaskInfo>()

    internal var mainTask: TaskInfo? = null

    fun init(name: String) {
        if (!CommonSdkParameters.measurePerformance) return
        mainTask = TaskInfo(name).apply { timers.add(this) }
    }

    fun finish() {
        if (!CommonSdkParameters.measurePerformance) return
        mainTask?.let { stop(it) }
        val printWriter: PrintWriter by sdkKodein.instance<PrintWriter>()
        printWriter.println(prettyPrint().also {
            Files.writeString(
                sdkSettings.sdkHome().resolve("perflogs").resolve(
                    LocalDateTime.now().format(
                        DateTimeFormatter.ofPattern(
                            "yyyy-MM-dd"
                        )
                    ) + "_perf.log"
                ).also {
                    if (!Files.exists(it)) {
                        Files.createDirectories(it.parent)
                        Files.createFile(it)
                    }
                },
                it,
                StandardOpenOption.WRITE,
                StandardOpenOption.APPEND
            )
            timers.clear()
        })
    }

    fun stop(task: TaskInfo) {
        task.stopTimeMillis = System.currentTimeMillis()
    }

    fun start(tag: String): TaskInfo {
        return TaskInfo(tag).also { timers.add(it) }
    }

    fun buildTaskStat(): Collection<TaskStat> = timers.map { it.taskName }.distinct().map { tag ->
        val taskStat = TaskStat(tag)
        val tasks = timers.filter { it.taskName == tag }
        if (tasks.isNotEmpty()) {
            taskStat.startTimeMillis = tasks.map { it.startTimeMillis }.min()
            taskStat.stopTimeMillis = tasks.map { it.stopTimeMillis }.filterNotNull().max()
            taskStat.count = tasks.size
            taskStat.minTimeMillis =
                tasks.filter { it.stopTimeMillis != null }.map { it.stopTimeMillis!! - it.startTimeMillis }.min()
            taskStat.maxTimeMillis =
                tasks.filter { it.stopTimeMillis != null }.map { it.stopTimeMillis!! - it.startTimeMillis }.max()
            taskStat.totalTimeMillis =
                tasks.filter { it.stopTimeMillis != null }.map { it.stopTimeMillis!! - it.startTimeMillis }.sum()
            taskStat.avgTimeMillis =
                tasks.filter { it.stopTimeMillis != null }.map { it.stopTimeMillis!! - it.startTimeMillis }
                    .average()
        }
        taskStat
    }.sortedBy { it.taskName }


    fun prettyPrint(): String {
        val mainTask = mainTask!!
        val sb = StringBuilder(mainTask.taskName)
        sb.append('\n')
        val totalTimeSeconds = mainTask.stopTimeMillis!! - mainTask.startTimeMillis
        val pad = 10
        val taskPad = timers.map { it.taskName.length + 1 }.max() ?: 10
        sb.append("${"-".padEnd(taskPad + (pad + 2) * 5, padChar = '-')}\n")
        sb.append(
            "${"Task name".padEnd(taskPad)}  ${"avg ms".padEnd(pad)}  ${"min ms".padEnd(pad)}  "
                    + "${"max ms".padEnd(pad)}  ${"total ms".padEnd(pad)}  ${"count".padEnd(pad)}\n"
        )
        sb.append("${"-".padEnd(taskPad + (pad + 2) * 5, padChar = '-')}\n")
        val nf = NumberFormat.getNumberInstance()
        nf.isGroupingUsed = false
        val pf = NumberFormat.getPercentInstance()
        pf.isGroupingUsed = false
        for (task in buildTaskStat()) {
            sb.append(task.taskName.padEnd(taskPad)).append(" ")
            sb.append(nf.format(task.avgTimeMillis.toLong()).padStart(pad)).append("  ")
            sb.append(nf.format(task.minTimeMillis).padStart(pad)).append("  ")
            sb.append(nf.format(task.maxTimeMillis).padStart(pad)).append("  ")
            sb.append(nf.format(task.totalTimeMillis).padStart(pad)).append("  ")
            sb.append(nf.format(task.count).padStart(pad)).append("\n")
        }
        sb.append("\n")
        return sb.toString()
    }

}