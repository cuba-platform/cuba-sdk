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

import com.haulmont.cuba.cli.plugin.sdk.dto.Repository
import com.haulmont.cuba.cli.plugin.sdk.dto.RepositoryTarget
import java.nio.file.Path

interface RepositoryManager {

    fun getRepositoryId(target: RepositoryTarget, name: String): String

    fun getRepository(name: String, target: RepositoryTarget): Repository?

    fun addRepository(repository: Repository, target: RepositoryTarget)

    fun removeRepository(name: String, target: RepositoryTarget, force: Boolean = false)

    fun getRepositories(target: RepositoryTarget): Collection<Repository>

    fun addPremiumRepository(licenseKey: String)

    fun isOnline(repository: Repository): Boolean

    fun buildMavenSettingsFile()

    fun mvnSettingFile(): Path
}