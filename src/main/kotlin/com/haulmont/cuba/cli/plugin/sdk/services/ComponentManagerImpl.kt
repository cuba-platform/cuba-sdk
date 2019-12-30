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

import com.google.gson.Gson
import com.haulmont.cuba.cli.cubaplugin.di.sdkKodein
import com.haulmont.cuba.cli.generation.VelocityHelper
import com.haulmont.cuba.cli.plugin.sdk.dto.*
import com.haulmont.cuba.cli.plugin.sdk.search.BintraySearch
import com.haulmont.cuba.cli.plugin.sdk.search.Nexus2Search
import com.haulmont.cuba.cli.plugin.sdk.search.Nexus3Search
import com.haulmont.cuba.cli.plugin.sdk.search.RepositorySearch
import org.kodein.di.generic.instance
import java.util.logging.Logger
import java.util.stream.Collectors

typealias ResolveProgressCallback = (component: Component, resolved: Float, total: Int) -> Unit
typealias UploadProcessCallback = (artifact: MvnArtifact, uploaded: Float, total: Int) -> Unit

class ComponentManagerImpl : ComponentManager {

    private val log: Logger = Logger.getLogger(ComponentManagerImpl::class.java.name)

    private val componentTemplates: ComponentTemplates by sdkKodein.instance()
    private val metadataHolder: MetadataHolder by sdkKodein.instance()
    private val repositoryManager: RepositoryManager by sdkKodein.instance()
    private val mvnArtifactManager: MvnArtifactManager by sdkKodein.instance()
    private val velocityHelper: VelocityHelper = VelocityHelper()

    private fun localProgress(component: Component) =
        1f / (3 + getClassifiersToResolve(component).size)

    override fun search(component: Component): Component? {
        val componentTemplate = findTemplate(component) ?: component
        return searchInExternalRepo(componentTemplate)?.let { resolved ->
            if (resolved.name == null || resolved.name.isBlank()) {
                resolved.components.find { it.name != null && it.name.endsWith("-global") }?.let {
                    return resolved.copy(name = it.name?.substringBefore("-global"))
                }
            }
            return resolved
        }
    }

    private fun matchTemplate(it: Component, component: Component) =
        listOfNotNull(it.name, it.packageName)
            .intersect(
                listOfNotNull(
                    component.name,
                    component.packageName
                )
            ).isNotEmpty() && it.type == component.type

    override fun isAlreadyInstalled(component: Component): Boolean {
        val componentTemplate = findTemplate(component) ?: component
        return metadataHolder.getMetadata().installedComponents.stream()
            .filter {
                it.type == componentTemplate.type &&
                        it.name == componentTemplate.name &&
                        it.packageName == componentTemplate.packageName &&
                        it.version == componentTemplate.version
            }
            .findAny()
            .isPresent
    }

    override fun searchInMetadata(component: Component): Component? {
        val componentTemplate = findTemplate(component) ?: component
        return metadataHolder.getMetadata().components.stream()
            .filter {
                it.type == componentTemplate.type &&
                        it.name == componentTemplate.name &&
                        it.packageName == componentTemplate.packageName &&
                        it.version == componentTemplate.version
            }
            .findAny()
            .orElse(null)
    }

    private fun findTemplate(component: Component): Component? =
        componentTemplates.getTemplates().searchTemplate(component)?.let {
            log.fine("Template for $component found")
            processComponentTemplate(component, it)
        }

    private fun processComponentTemplate(
        component: Component,
        template: Component
    ): Component? = Gson().fromJson<Component>(
        velocityHelper.generate(
            Gson().toJson(template), component.packageName,
            mapOf(
                "version" to component.version,
                "name" to (component.name ?: ""),
                "packageName" to component.packageName
            )
        ), Component::class.java
    )


    private fun Collection<Component>.searchTemplate(component: Component): Component? = find {
        matchTemplate(it, component)
    }

    private fun searchInExternalRepo(component: Component): Component? {
        log.info("Search component in external repo: ${component}")
        for (searchContext in repositoryManager.getRepositories(RepositoryTarget.SEARCH)) {
            initSearch(searchContext).search(component)?.let { return it }
        }
        return null
    }

    private fun initSearch(repository: Repository): RepositorySearch = when (repository.type) {
        RepositoryType.BINTRAY -> BintraySearch(repository)
        RepositoryType.NEXUS2 -> Nexus2Search(repository)
        RepositoryType.NEXUS3 -> Nexus3Search(repository)
        else -> throw IllegalStateException("Unsupported search context")
    }


    override fun resolve(component: Component, progress: ResolveProgressCallback?) {
        if (component.components.isNotEmpty()) {
            log.info("Resolve complex component: ${component}")
            val resolvedComponents = ArrayList<Component>()
            val total = component.components.size
            var resolved = 0f
            component.components.parallelStream().forEach { componentToResolve ->
                progress?.let { it(componentToResolve, resolved, total) }
                val resolvedComponent = resolveDependencies(componentToResolve) { _, localProgress, _ ->
                    progress?.let { it(componentToResolve, resolved + localProgress, total) }
                }
                resolved++
                resolvedComponent?.let { resolvedComponents.add(it) }
            }
            component.components.clear()
            component.components.addAll(resolvedComponents)
        } else {
            log.info("Resolve component: ${component}")
            resolveDependencies(component, progress)
        }
    }

    override fun upload(component: Component, progress: UploadProcessCallback?) {
        val artifacts = component.components.stream()
            .flatMap { it.dependencies.stream() }
            .collect(Collectors.toSet())

        artifacts.addAll(component.dependencies)

        val total = artifacts.size
        var uploaded = 0f

        artifacts.parallelStream().forEach { artifact ->
            mvnArtifactManager.upload(artifact)
            uploaded++
            progress?.let { it(artifact, uploaded, total) }
        }

        metadataHolder.getMetadata().installedComponents.add(
            component.copy(
                dependencies = HashSet(),
                components = HashSet()
            )
        )
        metadataHolder.flushMetadata()
    }

    override fun register(component: Component) {
        metadataHolder.getMetadata().components.add(component)
        metadataHolder.flushMetadata()
    }

    private fun resolveDependencies(component: Component, progress: ResolveProgressCallback?): Component? {
        if (ComponentType.LIB == component.type && component.name != null) {
            var progressCount = 0
            log.info("Resolve component dependencies: ${component}")
            val artifact = MvnArtifact(
                component.packageName,
                component.name,
                component.version
            )

            if (mvnArtifactManager.readPom(artifact) == null) {
                log.info("Component not found: ${component}")
                progress?.let { it(component, 1f, 1) }
                return null
            }

            for (classifier in component.classifiers) {
                mvnArtifactManager.getArtifact(artifact, classifier)
                artifact.classifiers.add(classifier)
            }

            val dependencies = mvnArtifactManager.resolve(artifact)

            log.fine("Component ${component} dependencies: ${dependencies}")
            progress?.let { it(component, localProgressCount(progressCount++, component), 1) }

            component.dependencies.add(artifact)
            component.dependencies.addAll(dependencies)

            progress?.let { it(component, localProgressCount(progressCount++, component), 1) }
            val additionalDependencies = component.dependencies.parallelStream()
                .flatMap { mvnArtifactManager.searchAdditionalDependencies(it).stream() }
                .collect(Collectors.toSet())

            component.dependencies.addAll(additionalDependencies)

            for (classifier in getClassifiersToResolve(component)) {
                if (classifier.type != "" || classifier.extension == "sdk") {
                    progress?.let { it(component, localProgressCount(progressCount++, component), 1) }
                    if (dependencies.isNotEmpty()) {
                        log.info("Resolve ${component.packageName} classifier \"${classifier}\" dependencies")
                        mvnArtifactManager.resolve(artifact, classifier)
                    }
                }
                component.dependencies.forEach { it.classifiers.add(classifier.copy()) }
            }

            progress?.let { it(component, localProgressCount(progressCount++, component), 1) }
            log.fine("Resolve ${component.packageName} classifiers")
            component.dependencies.parallelStream().forEach {
                mvnArtifactManager.resolveClassifiers(it)
            }

            return component
        }
        return null
    }

    private fun getClassifiersToResolve(component: Component): List<Classifier> {
        return listOf(
            Classifier.pom(),
            Classifier.default(),
            Classifier.sources(),
            Classifier.javadoc(),
            Classifier.client()
        )
    }

    private fun localProgressCount(
        progressCount: Int,
        component: Component
    ) = progressCount * localProgress(component)
}