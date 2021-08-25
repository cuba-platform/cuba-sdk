package com.haulmont.cli.plugin.sdk.component.cuba.providers

import com.haulmont.cli.plugin.sdk.component.cuba.dto.JmixComponent
import com.haulmont.cli.plugin.sdk.component.cuba.search.LocalRepositorySearch
import com.haulmont.cli.plugin.sdk.component.cuba.search.Nexus2Search
import com.haulmont.cli.plugin.sdk.component.cuba.search.Nexus3Search
import com.haulmont.cli.plugin.sdk.component.cuba.search.RepositorySearch
import com.haulmont.cuba.cli.plugin.sdk.dto.Component
import com.haulmont.cuba.cli.plugin.sdk.dto.MvnArtifact
import com.haulmont.cuba.cli.plugin.sdk.dto.Repository
import com.haulmont.cuba.cli.plugin.sdk.dto.RepositoryType
import com.haulmont.cuba.cli.plugin.sdk.services.ArtifactManager
import com.haulmont.cuba.cli.plugin.sdk.templates.NexusSearchComponentProvider
import java.nio.file.Paths

abstract class JmixProvider : NexusSearchComponentProvider("jmix") {

    companion object{
        val SEARCH_REPOS = listOf(
            Repository(
                name = "local",
                type = RepositoryType.LOCAL,
                url = Paths.get(System.getProperty("user.home")).resolve(".m2").resolve("repository").toString()
            ),
            Repository(
                name = "public",
                type = RepositoryType.NEXUS3,
                url = "https://global.repo.jmix.io/service/rest/v1/search"
            )
        )
    }

    internal val artifactManager: ArtifactManager by lazy { ArtifactManager.instance() }

    fun search(component: Component): Component? {
        searchInExternalRepo(component)?.let { resolved ->
            if (resolved.artifactId.isBlank()) {
                    resolved.artifactId = "jmix"
            }
            return resolved
        }
        val model = (component as JmixComponent).let {
            MvnArtifact(
                it.groupId,
                it.artifactId,
                it.version
            )
        }.let { artifactManager.readPom(it) }
        return if (model != null) component else null
    }

    protected fun searchInExternalRepo(component: Component): Component? {
        for (searchContext in SEARCH_REPOS) {
            initSearch(searchContext).search(component)?.let { return it }
        }
        return null
    }

    private fun initSearch(repository: Repository): RepositorySearch = when (repository.type) {
        RepositoryType.NEXUS2 -> Nexus2Search(repository)
        RepositoryType.NEXUS3 -> Nexus3Search(repository)
        RepositoryType.LOCAL -> LocalRepositorySearch(repository)
    }
}