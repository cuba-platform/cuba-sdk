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

package com.haulmont.cuba.cli.plugin.sdk.gradle

import org.gradle.tooling.GradleConnector
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.GradleTask
import org.gradle.tooling.model.idea.IdeaProject
import org.gradle.util.GradleVersion
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.*
import java.util.stream.Collectors


class GradleConnector(gradleInstallationDir: String?, projectDir: String?) {
    public val connector: GradleConnector
    val gradleVersion: String
        get() = GradleVersion.current().version

    val gradleTaskNames: List<String>
        get() {
            val taskNames: List<String> = ArrayList()
            val tasks = gradleTasks
            return tasks.stream()
                .map { task: GradleTask -> task.name }
                .collect(Collectors.toList())
        }

    val gradleTasks: List<GradleTask>
        get() {
            val tasks: MutableList<GradleTask> = ArrayList()
            val connection = connector.connect()
            connection.model(GradleProject::class.java)
            try {
                val project = connection.getModel(GradleProject::class.java)
                for (task in project.tasks) {
                    tasks.add(task)
                }
                connection.newBuild()
                    .withArguments("-PtoResolve=com.haulmont.addon.dashboard:dashboard-core:3.2.0.BETA1")
                    .forTasks("resolve")
                    .setStandardOutput(System.out)
                    .run()
            } finally {
                connection.close()
            }
            return tasks
        }

    init {
        connector = GradleConnector.newConnector()
        connector.useInstallation(File(gradleInstallationDir))
        connector.forProjectDirectory(File(projectDir))
    }

    fun runTask(name: String, params: Map<String, String>): String {
        val connection = connector.connect()
        connection.model(GradleProject::class.java)
        try {
            val project = connection.getModel(IdeaProject::class.java)
            val outputStream = ByteArrayOutputStream()

            connection.newBuild()
                .withArguments(params.map { "-P${it.key}=${it.value}" }.toList())
                .forTasks(name)
                .setStandardOutput(outputStream)
                .run()
            return outputStream.toString()
        } finally {
            connection.close()
        }
    }


}