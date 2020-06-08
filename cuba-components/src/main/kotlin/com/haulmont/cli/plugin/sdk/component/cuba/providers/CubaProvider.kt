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

package com.haulmont.cli.plugin.sdk.component.cuba.providers

import com.haulmont.cli.plugin.sdk.component.cuba.search.*
import com.haulmont.cuba.cli.plugin.sdk.di.sdkKodein
import com.haulmont.cuba.cli.plugin.sdk.dto.*
import com.haulmont.cuba.cli.plugin.sdk.services.ArtifactManager
import com.haulmont.cuba.cli.plugin.sdk.services.RepositoryManager
import com.haulmont.cuba.cli.plugin.sdk.templates.BintraySearchComponentProvider
import org.kodein.di.generic.instance
import java.nio.file.Paths

abstract class CubaProvider : BintraySearchComponentProvider() {

    companion object{
        val SEARCH_REPOS = listOf(
            Repository(
                name = "local",
                type = RepositoryType.LOCAL,
                url = Paths.get(System.getProperty("user.home")).resolve(".m2").resolve("repository").toString()
            ),
            Repository(
                name = "cuba-nexus",
                type = RepositoryType.NEXUS2,
                url = "https://repo.cuba-platform.com/service/local/lucene/search",
                authentication = Authentication(login = "cuba", password = "cuba123")
            ),
            Repository(
                name = "cuba-bintray",
                type = RepositoryType.BINTRAY,
                url = "https://api.bintray.com/search/packages/maven?",
                repositoryName = "cuba-platform"
            )
        )
    }

    internal val artifactManager: ArtifactManager by lazy { ArtifactManager.instance() }
    internal val repositoryManager: RepositoryManager by sdkKodein.instance<RepositoryManager>()

    fun search(component: Component): Component? {
        searchInExternalRepo(component)?.let { resolved ->
            if (resolved.artifactId.isBlank()) {
                resolved.components.find { it.artifactId.endsWith("-global") }?.let {
                    return resolved.copy(artifactId = it.artifactId.substringBefore("-global"))
                }
            }
            return resolved
        }
        val model = artifactManager.readPom(component.globalModule().let {
            MvnArtifact(
                component.groupId,
                component.artifactId,
                component.version
            )
        })
        return if (model != null) component else null
    }

    protected fun searchInExternalRepo(component: Component): Component? {
        for (searchContext in SEARCH_REPOS) {
            initSearch(searchContext).search(component)?.let { return it }
        }
        return null
    }

    private fun initSearch(repository: Repository): RepositorySearch = when (repository.type) {
        RepositoryType.BINTRAY -> BintraySearch(repository)
        RepositoryType.NEXUS2 -> Nexus2Search(repository)
        RepositoryType.NEXUS3 -> Nexus3Search(repository)
        RepositoryType.LOCAL -> LocalRepositorySearch(repository)
    }
}