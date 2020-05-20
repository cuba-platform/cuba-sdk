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
import com.haulmont.cuba.cli.plugin.sdk.dto.Classifier
import com.haulmont.cuba.cli.plugin.sdk.dto.Component
import com.haulmont.cuba.cli.plugin.sdk.dto.MvnArtifact
import com.haulmont.cuba.cli.plugin.sdk.dto.Repository
import com.haulmont.cuba.cli.plugin.sdk.nexus.NexusManager
import com.haulmont.cuba.cli.plugin.sdk.nexus.NexusScriptManager
import com.haulmont.cuba.cli.plugin.sdk.templates.ComponentRegistry
import com.haulmont.cuba.cli.plugin.sdk.utils.VelocityHelper
import com.haulmont.cuba.cli.plugin.sdk.utils.authorizeIfRequired
import com.haulmont.cuba.cli.plugin.sdk.utils.performance
import org.json.JSONObject
import org.kodein.di.generic.instance
import java.util.concurrent.atomic.AtomicInteger
import java.util.logging.Logger
import java.util.stream.Collectors

class ComponentManagerImpl : ComponentManager {

    private val log: Logger = Logger.getLogger(ComponentManagerImpl::class.java.name)

    private val componentTemplates: ComponentTemplates by sdkKodein.instance<ComponentTemplates>()
    private val metadataHolder: MetadataHolder by sdkKodein.instance<MetadataHolder>()
    private val repositoryManager: RepositoryManager by sdkKodein.instance<RepositoryManager>()
    private val artifactManager: ArtifactManager by sdkKodein.instance<ArtifactManager>()
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
        return "$groupUrl/$name/$version/$name-$version.${classifier.extension}"
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
        val (_, response, _) =
            Fuel.head(repoUrl(repository, componentUrl(artifact, classifier)))
                .authorizeIfRequired(repository)
                .response()
        return response.statusCode == 200
    }


    override fun upload(component: Component, repositories: List<Repository>, progress: UploadProcessCallback?) {
        val artifacts = component.collectAllDependencies()

        val total = artifacts.size
        val uploaded = AtomicInteger(0)

        artifactsStream(artifacts).forEach { artifact ->
            val repositoriesToUpload = repositories.filter {
                !alreadyUploaded(it, artifact)
            }
            if (repositoriesToUpload.isNotEmpty()) {
                artifactManager.upload(repositories, artifact)
            }
            progress?.let { it(artifact, uploaded.incrementAndGet(), total) }
        }

        metadataHolder.addInstalled(
            component.copy(
                dependencies = HashSet(),
                components = HashSet()
            )
        )
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
            log.info("Resolve component dependencies: ${component}")
            val artifact = MvnArtifact(
                component.groupId,
                component.artifactId,
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