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
import com.haulmont.cuba.cli.cubaplugin.di.sdkKodein
import com.haulmont.cuba.cli.generation.VelocityHelper
import com.haulmont.cuba.cli.plugin.sdk.commands.CommonSdkParameters
import com.haulmont.cuba.cli.plugin.sdk.dto.*
import com.haulmont.cuba.cli.plugin.sdk.nexus.NexusManager
import com.haulmont.cuba.cli.plugin.sdk.nexus.NexusScriptManager
import com.haulmont.cuba.cli.plugin.sdk.search.*
import com.haulmont.cuba.cli.plugin.sdk.utils.performance
import org.json.JSONObject
import org.kodein.di.generic.instance
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import java.util.logging.Logger
import java.util.stream.Collectors

class ComponentManagerImpl : ComponentManager {

    private val log: Logger = Logger.getLogger(ComponentManagerImpl::class.java.name)

    private val componentTemplates: ComponentTemplates by sdkKodein.instance()
    private val metadataHolder: MetadataHolder by sdkKodein.instance()
    private val repositoryManager: RepositoryManager by sdkKodein.instance()
    private val artifactManager: ArtifactManager by sdkKodein.instance()
    private val nexusManager: NexusManager by sdkKodein.instance()
    private val nexusScriptManager: NexusScriptManager by sdkKodein.instance()
    private val sdkSettings: SdkSettingsHolder by sdkKodein.instance()
    private val velocityHelper = VelocityHelper()

    private fun localProgress(component: Component) =
        1f / (3 + getClassifiersToResolve(component).size)

    override fun search(component: Component): Component? {
        val template = componentTemplates.findTemplate(component)
        return searchInExternalRepo(template ?: component)?.let { resolved ->
            if (resolved.name == null || resolved.name.isBlank()) {
                resolved.components.find { it.name != null && it.name.endsWith("-global") }?.let {
                    return resolved.copy(name = it.name?.substringBefore("-global"))
                }
            }
            return resolved
        } ?: template
    }

    override fun isAlreadyInstalled(component: Component): Boolean {
        val componentTemplate = componentTemplates.findTemplate(component) ?: component
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

    override fun searchInMetadata(component: Component): Component? =
        searchComponent(
            componentTemplates.findTemplate(component) ?: component,
            metadataHolder.getMetadata().components
        )

    private fun searchComponent(component: Component, components: Collection<Component>): Component? =
        components.stream()
            .filter {
                it.type == component.type &&
                        it.name == component.name &&
                        it.packageName == component.packageName &&
                        it.version == component.version
            }
            .findAny()
            .orElse(null)

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


    override fun resolve(component: Component, progress: ResolveProgressCallback?): Component? {
        progress?.let { it(component, 0f, 1) }
        if (component.components.isNotEmpty()) {
            if (ComponentType.FRAMEWORK == component.type) {
                performance("Resolve SDK BOM") {
                    resolveSdkBom(component)
                }
            }
            performance("Read framework version") {
                readFrameworkVersion(component)
            }
            log.info("Resolve complex component: ${component}")
            val resolvedComponents = ArrayList<Component>()
            val total = component.components.size
            val resolved = AtomicInteger(0)

            performance("Resolve all components") {
                componentResolveStream(component).forEach { componentToResolve ->
                    val resolvedComponent = performance("Resolve component") {
                        resolveDependencies(componentToResolve) { _, localProgress, _ ->
                            progress?.let { it(componentToResolve, resolved.get() + localProgress, total) }
                        }
                    }
                    resolvedComponent?.let { resolvedComponents.add(it) }
                    resolved.incrementAndGet()
                }
            }
//            progress?.let { it(component, 1f, 1) }
            component.components.clear()
            component.components.addAll(resolvedComponents)
        } else {
            log.info("Resolve component: ${component}")
            resolveDependencies(component, progress)
        }
        return component
    }

    private fun MutableSet<Component>.globalModule() =
        filter { it.name != null && it.name.endsWith("-global") }.firstOrNull()

    private fun readFrameworkVersion(component: Component) {
        if (listOf(ComponentType.FRAMEWORK, ComponentType.ADDON).contains(component.type)) {
            component.components.globalModule()?.let {
                val model = artifactManager.readPom(
                    MvnArtifact(
                        it.packageName,
                        it.name!!,
                        it.version
                    )
                )
                if (model == null) {
                    log.info("Component not found: ${component}")
                    throw IllegalStateException("Component not found: ${component}")
                }
                component.frameworkVersion =
                    model.dependencies.filter { it.artifactId == "cuba-global" }.map { it.version }.firstOrNull()
            }
        }
    }

    override fun searchForAdditionalComponents(component: Component): Set<Component> {
        val additionalComponentList = mutableSetOf<Component>()
        if (listOf(ComponentType.FRAMEWORK, ComponentType.ADDON).contains(component.type)) {
            component.components.globalModule()?.let {
                val model = artifactManager.readPom(
                    MvnArtifact(
                        it.packageName,
                        it.name!!,
                        it.version
                    )
                )
                if (model == null) {
                    log.info("Component not found: ${component}")
                    throw IllegalStateException("Component not found: ${component}")
                }
                model.dependencies.filter { it.artifactId.endsWith("-global") && !it.artifactId.startsWith("cuba") }
                    .forEach {
                        search(
                            Component(
                                it.groupId,
                                it.artifactId.substringBeforeLast("-global"),
                                it.version,
                                type = ComponentType.ADDON
                            )
                        )?.let {
                            additionalComponentList.add(it)
                            additionalComponentList.addAll(searchForAdditionalComponents(it))
                        }
                    }
            }
        }
        return additionalComponentList
    }

    private fun resolveRawComponent(component: Component): Component? {
        for (classifier in component.classifiers) {
            val componentPath = Path.of(sdkSettings["maven.local.repo"])
                .resolve(component.packageName)
                .resolve(component.name)
                .resolve(component.version)
                .resolve("${component.name}-${component.version}.${classifier.extension}")
            Files.createDirectories(componentPath.parent)
            if (component.url != null) {
                val (_, response, _) = Fuel.download(component.url)
                    .destination { response, Url ->
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
        val model = artifactManager.readPom(
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
                        url = sdkSettings["gradle.downloadLink"].format(gradleVersion),
                        version = gradleVersion,
                        classifiers = mutableListOf(Classifier("", "zip")),
                        type = ComponentType.RAW
                    )
                )
            }
        }
    }

    override fun upload(component: Component, repositories: List<Repository>, progress: UploadProcessCallback?) {
        val artifacts = component.collectAllDependencies()

        val total = artifacts.size
        val uploaded = AtomicInteger(0)

        artifactsStream(artifacts).forEach { artifact ->
            artifactManager.upload(repositories, artifact)
            progress?.let { it(artifact, uploaded.incrementAndGet(), total) }
        }

        metadataHolder.getMetadata().installedComponents.add(
            component.copy(
                dependencies = HashSet(),
                components = HashSet()
            )
        )
        metadataHolder.flushMetadata()
    }

    override fun remove(componentToRemove: Component, removeFromRepo: Boolean, progress: RemoveProcessCallback?) {
        searchComponent(componentToRemove, metadataHolder.getMetadata().components)?.let { component ->
            val allOtherDependencies = metadataHolder.getMetadata().components
                .filter { it != component }
                .flatMap { it.collectAllDependencies() }
            val dependencies = component.collectAllDependencies()
            val total = dependencies.size
            val removed = AtomicInteger(0)
            dependencies.forEach { artifact ->
                if (!allOtherDependencies.contains(artifact)) {
                    removeArtifact(artifact, removeFromRepo)
                }
                progress?.let { it(artifact, removed.incrementAndGet(), total) }
            }
            removeFromMetadata(component)
            metadataHolder.flushMetadata()
        }
    }

    private fun removeArtifact(artifact: MvnArtifact, removeFromRepo: Boolean) {
        artifactManager.remove(artifact)
        if (removeFromRepo && nexusManager.isLocal()) {
            nexusScriptManager.run(
                sdkSettings["repository.login"], sdkSettings["repository.password"], "sdk.drop-component", JSONObject()
                    .put("repoName", sdkSettings["repository.name"])
                    .put("artifact", artifact.mvnCoordinates())
            )
        }
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
        artifacts.stream()

    override fun register(component: Component) {
        removeFromMetadata(component)
        addToMetadata(component)
        metadataHolder.flushMetadata()
    }

    private fun addToMetadata(component: Component) {
        metadataHolder.getMetadata().components.add(component)
    }

    private fun removeFromMetadata(component: Component) {
        searchComponent(component, metadataHolder.getMetadata().components)?.let {
            metadataHolder.getMetadata().components.remove(it)
        }
        searchComponent(component, metadataHolder.getMetadata().installedComponents)?.let {
            metadataHolder.getMetadata().installedComponents.remove(it)
        }
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

            if (artifactManager.readPom(artifact) == null) {
                log.info("Component not found: ${component}")
                progress?.let { it(component, 1f, 1) }
                return null
            }

            performance("Read all classifiers") {
                for (classifier in component.classifiers) {
                    performance("Read classifier $classifier") {
                        artifactManager.getOrDownloadArtifactWithClassifiers(artifact, component.classifiers)
                    }
                }
                artifact.classifiers.addAll(component.classifiers)
            }

            val dependencies: List<MvnArtifact> = performance("Resolve") {
                artifactManager.resolve(artifact)
            }

            log.fine("Component ${component} dependencies: ${dependencies}")
            progress?.let { it(component, localProgressCount(progressCount++, component), 1) }

            component.dependencies.add(artifact)
            component.dependencies.addAll(dependencies)

            progress?.let { it(component, localProgressCount(progressCount++, component), 1) }

            val additionalDependencies = performance("Search additional dependencies") {
                artifactsStream(component.dependencies)
                    .flatMap { searchAdditionalDependencies(it).stream() }
                    .collect(Collectors.toSet())
            }

            component.dependencies.addAll(additionalDependencies)

            performance("Resolve all classifiers") {
                for (classifier in getClassifiersToResolve(component)) {
                    progress?.let { it(component, localProgressCount(progressCount++, component), 1) }
//                    if (classifier.type != "" || classifier.extension == "sdk") {
//                        if (dependencies.isNotEmpty()) {
//                            log.info("Resolve $component classifier \"$classifier\" dependencies")
//                            performance("Resolve classifier \"$classifier\" dependencies") {
//                                artifactManager.resolve(artifact, classifier)
//                            }
//                        }
//                    }
                    component.dependencies.forEach { it.classifiers.add(classifier.copy()) }
                }
            }

            progress?.let { it(component, localProgressCount(progressCount++, component), 1) }
            log.fine("Resolve ${component.packageName} classifiers")
            performance("Check classifiers resolved") {
                artifactsStream(component.dependencies).forEach {
                    artifactManager.checkClassifiers(it)
                }
            }
            progress?.let { it(component, localProgressCount(progressCount, component), 1) }

            return component
        }
        if (ComponentType.RAW == component.type) {
            resolveRawComponent(component)?.let { return it }
        }
        return null
    }

    private fun searchAdditionalDependencies(artifact: MvnArtifact): List<MvnArtifact> {
        try {
            val model = artifactManager.readPom(artifact)
            if (model == null || model.dependencyManagement == null) return ArrayList()
            return performance("Search additional dependencies") {
                model.dependencyManagement.dependencies.stream()
                    .filter { it.type == "pom" }
                    .flatMap { dependency ->
                        val version = if (dependency.version.startsWith("\${")) {
                            val propertiesMap = HashMap<String, String>()
                            for (entry in (model.properties.toMap() as Map<String, String>).entries) {
                                propertiesMap.put(entry.key.replace(".", "_"), entry.value)
                            }
                            propertiesMap.put("project_version", model.version)
                            val version = dependency.version.replace(".", "_")
                            velocityHelper.generate(
                                version,
                                dependency.groupId,
                                propertiesMap
                            )
                        } else {
                            dependency.version
                        }
                        val artifactList = ArrayList<MvnArtifact>()
                        val artifact = MvnArtifact(
                            dependency.groupId, dependency.artifactId, version,
                            classifiers = arrayListOf(Classifier("", dependency.type ?: "jar"))
                        )
                        artifactList.add(artifact)
                        artifactList.addAll(searchAdditionalDependencies(artifact))
                        return@flatMap artifactList.stream()
                    }.collect(Collectors.toList())
            }
        } catch (e: Exception) {
            log.throwing(
                MvnArtifactManagerImpl::class.java.name,
                "Error on reading pom file for ${artifact.mvnCoordinates()}",
                e
            )
            return ArrayList()
        }
    }

    private fun getClassifiersToResolve(component: Component): List<Classifier> {
        return listOf(
            Classifier.pom(),
            Classifier.default(),
            Classifier.sources()
        )
    }

    private fun localProgressCount(
        progressCount: Int,
        component: Component
    ) = progressCount * localProgress(component)
}