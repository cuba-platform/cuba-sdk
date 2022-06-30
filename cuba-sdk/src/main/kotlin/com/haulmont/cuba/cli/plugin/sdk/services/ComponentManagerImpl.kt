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
import com.haulmont.cuba.cli.plugin.sdk.commands.CommonSdkParameters
import com.haulmont.cuba.cli.plugin.sdk.di.sdkKodein
import com.haulmont.cuba.cli.plugin.sdk.dto.*
import com.haulmont.cuba.cli.plugin.sdk.nexus.NexusManager
import com.haulmont.cuba.cli.plugin.sdk.nexus.NexusScriptManager
import com.haulmont.cuba.cli.plugin.sdk.templates.ComponentRegistry
import com.haulmont.cuba.cli.plugin.sdk.utils.VelocityHelper
import com.haulmont.cuba.cli.plugin.sdk.utils.authorizeIfRequired
import com.haulmont.cuba.cli.plugin.sdk.utils.performance
import org.apache.maven.model.Dependency
import org.apache.maven.model.Model
import org.json.JSONObject
import org.kodein.di.generic.instance
import java.io.PrintWriter
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import java.util.logging.Logger
import java.util.stream.Collectors

class ComponentManagerImpl : ComponentManager {

    private val log: Logger = Logger.getLogger(ComponentManagerImpl::class.java.name)

    private val printWriter: PrintWriter by sdkKodein.instance<PrintWriter>()

    private val componentTemplates: ComponentTemplates by sdkKodein.instance<ComponentTemplates>()
    private val metadataHolder: MetadataHolder by sdkKodein.instance<MetadataHolder>()
    private val repositoryManager: RepositoryManager by sdkKodein.instance<RepositoryManager>()
    private val artifactManager: ArtifactManager by lazy { ArtifactManager.instance() }
    private val nexusManager: NexusManager by sdkKodein.instance<NexusManager>()
    private val nexusScriptManager: NexusScriptManager by sdkKodein.instance<NexusScriptManager>()
    private val sdkSettings: SdkSettingsHolder by sdkKodein.instance<SdkSettingsHolder>()
    private val velocityHelper = VelocityHelper()
    private val componentRegistry: ComponentRegistry by sdkKodein.instance<ComponentRegistry>()

    private fun localProgress(component: Component) =
        1f / (3 + getClassifiersToResolve(component).size)

    private fun repoUrl(repository: Repository, endpoint: String) = repository.url + endpoint

    private fun componentUrl(
        artifact: MvnArtifact,
        classifier: Classifier
    ): String {
        val groupUrl = artifact.groupId.replace(".", "/")
        val name = artifact.artifactId
        val version = artifact.version
        val classifierDesc = if (classifier.type == "") "" else "-${classifier.type}"
        return "$groupUrl/$name/$version/$name-$version$classifierDesc.${classifier.extension}"
    }

    override fun isAlreadyInstalled(component: Component): Boolean {
        val componentTemplate = componentTemplates.findTemplate(component) ?: component
        return metadataHolder.getInstalled().stream()
            .filter { it.isSame(componentTemplate) }
            .findAny()
            .isPresent
    }

    override fun searchInMetadata(component: Component): Component? =
        searchComponent(
            componentTemplates.findTemplate(component) ?: component,
            metadataHolder.getResolved()
        )

    private fun searchComponent(component: Component, components: Collection<Component>): Component? =
        components.find { it.isSame(component) }

    override fun resolve(component: Component, progress: ResolveProgressCallback?): Component? {
        progress?.let { it(component, 0f, 1) }
        if (component.components.isNotEmpty()) {
            log.fine("Resolve complex component: ${component}")
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
            log.fine("Resolve component: ${component}")
            resolveDependencies(component, progress)
        }
        return component
    }

    override fun searchForAdditionalComponents(component: Component): Set<Component> =
        componentRegistry.providerByName(component.type).searchAdditionalComponents(component)

    private fun resolveRawComponent(component: Component): Component? {
        val dependencies = artifactManager.uploadComponentToLocalCache(component)
        if (dependencies.isNotEmpty()) {
            component.dependencies.addAll(dependencies)
            return component
        } else {
            return null
        }
    }

    private fun alreadyUploaded(repository: Repository, artifact: MvnArtifact): Boolean {
        for (classifier in artifact.classifiers) {
            if (!alreadyUploaded(repository, artifact, classifier)) {
                return false
            }
        }
        return true
    }

    private fun alreadyUploaded(
        repository: Repository,
        artifact: MvnArtifact,
        classifier: Classifier
    ): Boolean {
        if (repository.type == RepositoryType.LOCAL) {
            return Files.exists(artifact.localPath(Path.of(repository.url), classifier))
        } else {
            val (_, response, _) =
                Fuel.head(repoUrl(repository, componentUrl(artifact, classifier)))
                    .authorizeIfRequired(repository)
                    .response()
            return response.statusCode == 200
        }
    }


    override fun upload(
        component: Component,
        repositories: List<Repository>,
        isImported: Boolean,
        progress: UploadProcessCallback?
    ) {
        val artifacts = component.collectAllDependencies()

        val total = artifacts.size
        val uploaded = AtomicInteger(0)

        artifactsStream(artifacts).forEach { artifact ->
            val repositoriesToUpload = repositories.filter {
                !alreadyUploaded(it, artifact)
            }
            if (repositoriesToUpload.isNotEmpty()) {
                artifactManager.upload(repositories, artifact, isImported)
            }
            progress?.let { it(artifact, uploaded.incrementAndGet(), total) }
        }


        val copy = component.clone()
        copy.dependencies = HashSet()
        copy.components = HashSet()
        metadataHolder.addInstalled(copy)
    }

    override fun remove(componentToRemove: Component, removeFromRepo: Boolean, progress: RemoveProcessCallback?) {
        searchComponent(componentToRemove, metadataHolder.getResolved())?.let { component ->
            val allOtherDependencies = metadataHolder.getResolved()
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
        }
    }

    private fun removeArtifact(artifact: MvnArtifact, removeFromRepo: Boolean) {
        artifactManager.remove(artifact)
        if (removeFromRepo && nexusManager.isLocal()) {
            nexusScriptManager.run(
                sdkSettings["repository.login"],
                repositoryManager.getLocalRepositoryPassword() ?: "",
                "sdk.drop-component",
                JSONObject()
                    .put("repoName", sdkSettings["repository.name"])
                    .put("artifact", artifact.mvnCoordinates())
            )
        }
    }

    private fun componentResolveStream(component: Component) =
        if (CommonSdkParameters.singleThread) component.components.stream() else component.components.parallelStream()

    private fun artifactsStream(artifacts: Set<MvnArtifact>) =
        artifacts.stream()

    override fun register(component: Component) {
        removeFromMetadata(component)
        addToMetadata(component)
    }

    private fun addToMetadata(component: Component) {
        metadataHolder.addResolved(component)
    }

    private fun removeFromMetadata(component: Component) {
        metadataHolder.removeResolved(component)
        metadataHolder.removeInstalled(component)
    }

    private fun resolveDependencies(component: Component, progress: ResolveProgressCallback? = null): Component? {
        if (component.url == null) {
            var progressCount = 0
            log.fine("Resolve component dependencies: ${component}")
            val artifact = MvnArtifact(
                component.groupId,
                component.artifactId,
                component.version
            )

            var pomClassifier = component.classifiers.filter { it == Classifier.pom() }.firstOrNull()
            if (pomClassifier == null) {
                pomClassifier = component.classifiers.filter { it == Classifier.sdk() }.firstOrNull()
            }

            if (pomClassifier == null) {
                log.info("Component not found: ${component}")
                progress?.let { it(component, 1f, 1) }
                return null
            }

            val pomModel = artifactManager.readPom(artifact, pomClassifier)
            if (pomModel == null) {
                log.info("Component not found: ${component}")
                progress?.let { it(component, 1f, 1) }
                return null
            }

            performance("Read all classifiers") {
                artifactManager.getOrDownloadArtifactWithClassifiers(artifact, component.classifiers)
            }

            performance("Read classifier $component.classifiers") {
                for (classifier in component.classifiers) {
                    artifactManager.getOrDownloadArtifactFile(artifact, classifier)?.let {
                        artifact.classifiers.add(classifier)
                    }
                }
            }

            val dependencies: Collection<MvnArtifact> = performance("Resolve all") {
                performance("Resolve") {
                    artifactManager.resolve(
                        artifact,
                        artifact.mainClassifier()
                    )
                }.let { dependencies ->
                    val list = dependencies.toMutableSet()

                    performance("Read unresolved dependencies") {
                        val unresolvedDependencies = collectUnresolvedDependencies(dependencies.toMutableSet())
                        unresolvedDependencies.forEach {
                            artifactManager.getOrDownloadArtifactWithClassifiers(it, it.classifiers)

                            for (classifier in it.classifiers) {
                                artifactManager.getOrDownloadArtifactFile(it, classifier)?.let { _ ->
                                    it.classifiers.add(classifier)
                                }
                            }
                        }

                        list.addAll(unresolvedDependencies)
                    }

                    pomModel.dependencies.filter { it.groupId == "org.codehaus.groovy" && it.artifactId == "groovy-all" }
                        .forEach { list.add(MvnArtifact(it.groupId, it.artifactId, it.version)) }

                    performance("Read all parents") {
                        dependencies.parallelStream().forEach {
                            performance("Read parents ${it.mvnCoordinates()}") {
                                list.addAll(readParentDependencies(it))
                            }
                        }
                    }
                    return@let list
                }
            }

            log.fine("Component ${component} dependencies: ${dependencies}")
            progress?.let { it(component, localProgressCount(progressCount++, component), 1) }

            var artifactContainsInDependencies = false

            dependencies.forEach {
                if (it.isSame(artifact)) {
                    it.classifiers.addAll(artifact.classifiers)
                    artifactContainsInDependencies = true
                }
            }

            if (!artifactContainsInDependencies) {
                component.dependencies.add(artifact)
            }
            component.dependencies.addAll(dependencies)

            progress?.let { it(component, localProgressCount(progressCount++, component), 1) }

            val additionalDependencies = performance("Search additional dependencies") {
                artifactsStream(component.dependencies)
                    .flatMap { searchAdditionalDependencies(it, null).stream() }
                    .collect(Collectors.toSet())
            }

            component.dependencies.addAll(additionalDependencies)

            performance("Resolve all classifiers") {
                for (classifier in getClassifiersToResolve(component)) {
                    progress?.let { it(component, localProgressCount(progressCount++, component), 1) }
                    component.dependencies.forEach { it.classifiers.add(classifier.copy()) }
                }
            }

            progress?.let { it(component, localProgressCount(progressCount++, component), 1) }
            log.fine("Resolve ${component.groupId} classifiers")
            performance("Check classifiers resolved") {
                artifactsStream(component.dependencies).forEach {
                    artifactManager.checkClassifiers(it)
                }
            }
            progress?.let { it(component, localProgressCount(progressCount, component), 1) }

            return component
        } else {
            resolveRawComponent(component)?.let { return it }
        }
        return null
    }

    fun readParentDependencies(mvnArtifact: MvnArtifact): Collection<MvnArtifact> {
        try {
            artifactManager.readPom(mvnArtifact)?.let { model ->
                model.parent?.let {
                    val parentArtifact = MvnArtifact(
                        it.groupId, it.artifactId, it.version, mutableSetOf(Classifier.pom())
                    )
                    artifactManager.getOrDownloadArtifactFile(mvnArtifact, Classifier.pom())
                    val list = mutableListOf(parentArtifact)
                    list.addAll(readParentDependencies(parentArtifact))
                    return list
                }
            }
        } catch (ignored: Exception) {
//            printWriter.println("UNABLE TO READ POM: $mvnArtifact")
        }

        return emptyList()
    }

    private fun collectUnresolvedDependencies(mvnArtifacts: Collection<MvnArtifact>): Set<MvnArtifact> {
        val dependencies = mutableSetOf<MvnArtifact>()

        mvnArtifacts.parallelStream().forEach {
            artifactManager.readPom(it)
                ?.dependencies
                ?.filter { dependency ->
                    dependency.version != null && !dependency.version.contains("$")
                }
                ?.filter { dependency ->
                    mvnArtifacts.none { mvnArtifact ->
                        mvnArtifact.groupId == dependency.groupId
                                && mvnArtifact.artifactId == dependency.artifactId
                    }
                }
                ?.forEach { dependency ->
                    dependencies.add(
                        MvnArtifact(
                            dependency.groupId,
                            dependency.artifactId,
                            dependency.version,
                            mutableSetOf(Classifier.jar(), Classifier.pom())
                        )
                    )
                }
        }

        return dependencies
    }

    private fun searchAdditionalDependencies(artifact: MvnArtifact, prevArtifact: MvnArtifact?): List<MvnArtifact> {
        try {
            val model = artifactManager.readPom(artifact)
            if (model == null) return ArrayList()

            var parentArtifact = artifact

            model.parent?.let {
                parentArtifact = MvnArtifact(
                    it.groupId, it.artifactId, it.version, mutableSetOf(Classifier.pom())
                )
            }

            val artifactList = ArrayList<MvnArtifact>()

            if (model.dependencyManagement == null) {
                if (!parentArtifact.isSame(artifact)) {
                    artifactList.add(parentArtifact)
                    artifactList.addAll(searchAdditionalDependencies(parentArtifact, artifact))
                    return artifactList
                }

                return ArrayList()
            }

            if (!parentArtifact.isSame(artifact)) {
                artifactList.add(parentArtifact)
                artifactList.addAll(searchAdditionalDependencies(parentArtifact, artifact))
            }

            artifactList.addAll(performance("Search additional dependencies") {
                model.dependencyManagement.dependencies.stream()
                    .filter { it.type == "pom" }
                    .flatMap { dependency ->
                        val version = getDependencyVersion(model, dependency)

                        val depsArtifactList = ArrayList<MvnArtifact>()
                        val dependencyArtifact = MvnArtifact(
                            dependency.groupId, dependency.artifactId, version,
                            classifiers = mutableSetOf(Classifier("", dependency.type ?: "jar"))
                        )

                        if (prevArtifact == null || !dependencyArtifact.isSame(prevArtifact)) {
                            depsArtifactList.add(dependencyArtifact)
                            depsArtifactList.addAll(searchAdditionalDependencies(dependencyArtifact, artifact))
                        }

                        return@flatMap depsArtifactList.stream()
                    }.collect(Collectors.toList())
            })

            return artifactList
        } catch (e: Exception) {
            log.throwing(
                ComponentManagerImpl::class.java.name,
                "Error on reading pom file for ${artifact.mvnCoordinates()}",
                e
            )
            return ArrayList()
        }
    }

    private fun getClassifiersToResolve(component: Component): List<Classifier> {
        return listOf(
            Classifier.pom(),
            Classifier.jar(),
            Classifier.sources()
        )
    }

    private fun localProgressCount(
        progressCount: Int,
        component: Component
    ) = progressCount * localProgress(component)

    private fun getDependencyVersion(model: Model, dependency: Dependency): String {

        if (dependency.version.startsWith("\${")) {
            val propertiesMap = HashMap<String, String>()
            for (entry in (model.properties.toMap() as Map<String, String>).entries) {
                propertiesMap.put(entry.key.replace(".", "_"), entry.value)
            }
            propertiesMap.put("project_version", model.version)
            val version = dependency.version.replace(".", "_")

            val resolvedVersion = velocityHelper.generate(
                version,
                dependency.groupId,
                propertiesMap
            )

            if (resolvedVersion == version) {

                if (model.parent != null) {
                    model.parent.let {
                        val parentArtifact = MvnArtifact(
                            it.groupId, it.artifactId, it.version, mutableSetOf(Classifier.pom())
                        )
                        val parentModel = artifactManager.readPom(parentArtifact)

                        if (parentModel != null) {
                            return getDependencyVersion(parentModel, dependency)
                        } else {
                            log.throwing(
                                ComponentManagerImpl::class.java.name,
                                "Error on reading pom file for ${parentArtifact.mvnCoordinates()}",
                                Exception()
                            )
                            return version
                        }

                    }
                } else {
                    log.throwing(
                        ComponentManagerImpl::class.java.name,
                        "Error: parent for dependency ${dependency} is absent",
                        Exception()
                    )
                    return version
                }
            } else {
                return resolvedVersion
            }
        }

        return dependency.version

    }
}