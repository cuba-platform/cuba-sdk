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

import com.github.kittinunf.fuel.Fuel
import com.google.gson.Gson
import com.haulmont.cuba.cli.cubaplugin.di.sdkKodein
import com.haulmont.cuba.cli.generation.VelocityHelper
import com.haulmont.cuba.cli.plugin.sdk.commands.CommonSdkParameters
import com.haulmont.cuba.cli.plugin.sdk.dto.*
import com.haulmont.cuba.cli.plugin.sdk.search.*
import org.kodein.di.generic.instance
import java.nio.file.Files
import java.nio.file.Path
import java.util.logging.Logger
import java.util.stream.Collectors

class ComponentManagerImpl : ComponentManager {

    private val log: Logger = Logger.getLogger(ComponentManagerImpl::class.java.name)

    private val componentTemplates: ComponentTemplates by sdkKodein.instance()
    private val metadataHolder: MetadataHolder by sdkKodein.instance()
    private val repositoryManager: RepositoryManager by sdkKodein.instance()
    private val mvnArtifactManager: MvnArtifactManager by sdkKodein.instance()
    private val sdkSettingsHolder: SdkSettingsHolder by sdkKodein.instance()
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
        RepositoryType.LOCAL -> LocalRepositorySearch(repository)
    }


    override fun resolve(component: Component, progress: ResolveProgressCallback?) {
        if (component.components.isNotEmpty()) {
            if (ComponentType.FRAMEWORK == component.type) {
                resolveSdkBom(component)
            }
            log.info("Resolve complex component: ${component}")
            val resolvedComponents = ArrayList<Component>()
            val total = component.components.size
            var resolved = 0f

            componentResolveStream(component).forEach { componentToResolve ->
                progress?.let { it(componentToResolve, resolved, total) }
                val resolvedComponent = resolveDependencies(componentToResolve) { _, localProgress, _ ->
                    progress?.let { it(componentToResolve, resolved + localProgress, total) }
                }
                resolvedComponent?.let { resolvedComponents.add(it) }
                resolved++

            }
            component.components.clear()
            component.components.addAll(resolvedComponents)
        } else {
            log.info("Resolve component: ${component}")
            resolveDependencies(component, progress)
        }
    }

    private fun resolveRawComponent(component: Component): Component? {
        for (classifier in component.classifiers) {
            val componentPath = Path.of(sdkSettingsHolder["maven.local.repo"])
                .resolve(component.packageName)
                .resolve(component.name)
                .resolve(component.version)
                .resolve("${component.name}-${component.version}.${classifier.extension}")
            Files.createDirectories(componentPath.parent)
            if (component.url != null) {
                val (_, response, _) = Fuel.download(component.url)
                    .fileDestination { response, Url ->
                        componentPath.toFile()
                    }
                    .response()
                if (response.statusCode == 200) {
                    component.dependencies.add(
                        MvnArtifact(
                            component.packageName,
                            component.name!!,
                            component.version,
                            classifiers = component.classifiers
                        )
                    )
                    return component
                }
            }
        }
        return null
    }

    private fun resolveSdkBom(component: Component) {
        val model = mvnArtifactManager.readPom(
            MvnArtifact("com.haulmont.gradle", "cuba-plugin", component.version),
            Classifier.sdk()
        )
        if (model != null) {
            val tomcatVersion = model.properties["tomcat.version"] as String?
            if (tomcatVersion != null) {
                component.components.add(
                    Component(
                        "org.apache.tomcat", "tomcat", tomcatVersion, classifiers = mutableListOf(
                            Classifier.pom(),
                            Classifier("", "zip")
                        )
                    )
                )
            }
            val gradleVersion = model.properties["gradle.version"] as String?
            if (gradleVersion != null) {
                component.components.add(
                    Component(
                        packageName = "gradle",
                        name = "gradle",
                        url = sdkSettingsHolder["gradle.downloadLink"].format(gradleVersion),
                        version = gradleVersion,
                        classifiers = mutableListOf(Classifier("", "zip")),
                        type = ComponentType.RAW
                    )
                )
            }
        }
    }

    override fun upload(component: Component, repository: Repository?, progress: UploadProcessCallback?) {
        val artifacts = component.components.stream()
            .flatMap { it.dependencies.stream() }
            .collect(Collectors.toSet())

        artifacts.addAll(component.dependencies)

        val total = artifacts.size
        var uploaded = 0f

        artifactsStream(artifacts).forEach { artifact ->
            repositoriesToUpload(repository).forEach {
                mvnArtifactManager.upload(it, artifact)
            }
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

    private fun repositoriesToUpload(repository: Repository?): List<Repository> =
        if (repository != null)
            listOf(repository)
        else repositoryManager.getRepositories(RepositoryTarget.TARGET)
            .filter {
                it.type in listOf(
                    RepositoryType.NEXUS3,
                    RepositoryType.NEXUS2,
                    RepositoryType.BINTRAY
                )
            }


    private fun componentResolveStream(component: Component) =
        if (CommonSdkParameters.singleThread) component.components.stream() else component.components.parallelStream()

    private fun artifactsStream(artifacts: Set<MvnArtifact>) =
        if (CommonSdkParameters.singleThread) artifacts.stream() else artifacts.parallelStream()

    override fun register(component: Component) {
        metadataHolder.getMetadata().components.add(component)
        metadataHolder.flushMetadata()
    }

    private fun resolveDependencies(component: Component, progress: ResolveProgressCallback? = null): Component? {
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
            val additionalDependencies = artifactsStream(component.dependencies)
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
            artifactsStream(component.dependencies).forEach {
                mvnArtifactManager.resolveClassifiers(it)
            }

            return component
        }
        if (ComponentType.RAW == component.type) {
            resolveRawComponent(component)?.let { return it }
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