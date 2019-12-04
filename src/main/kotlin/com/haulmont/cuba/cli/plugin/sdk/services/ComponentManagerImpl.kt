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

import com.github.kittinunf.fuel.core.Headers
import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.fuel.json.responseJson
import com.haulmont.cuba.cli.cubaplugin.di.sdkKodein
import com.haulmont.cuba.cli.plugin.sdk.dto.*
import org.json.JSONArray
import org.json.JSONObject
import org.kodein.di.generic.instance
import java.util.stream.Collectors

typealias ResolveProgressCallback = (component: Component, resolved: Int, total: Int) -> Unit
typealias UploadProcessCallback = (artifact: MvnArtifact, uploaded: Int, total: Int) -> Unit

class ComponentManagerImpl : ComponentManager {

    internal val metadataHolder: MetadataHolder by sdkKodein.instance()
    internal val sdkSettings: SdkSettingsHolder by sdkKodein.instance()
    internal val mvnArtifactManager: MvnArtifactManager by sdkKodein.instance()

    override fun search(context: SearchContext): Component? {
        return searchInMetadata(context) ?: searchInExternalRepo(context)
    }

    private fun searchInMetadata(context: SearchContext): Component? {
        return metadataHolder.getMetadata().components.stream()
            .filter {
                it.type == context.type &&
                        it.name == context.name &&
                        it.packageName == context.componentPackage &&
                        it.version == context.version
            }
            .findAny()
            .orElse(null)
    }

    private fun searchInExternalRepo(context: SearchContext): Component? {
        return when (sdkSettings.getProperty("search-repo-type")) {
            "bintray" -> searchBintray(context)
            else -> throw IllegalStateException("Unsupported source repo type: ${sdkSettings.getProperty("search-repo-type")}")
        }
    }

    override fun resolve(component: Component, progress: ResolveProgressCallback?) {
        if (component is ComplexComponent) {
            val resolvedComponents = ArrayList<Component>()
            val total = component.components.size
            var resolved = 0
            component.components.parallelStream().forEach { componentToResolve ->
                val resolvedComponent = searchInMetadata(
                    SearchContext(
                        ComponentType.LIB,
                        componentToResolve.packageName,
                        componentToResolve.name,
                        componentToResolve.version
                    )
                ) ?: resolveDependencies(componentToResolve)
                resolvedComponent?.let { resolvedComponents.add(it) }
                resolved++
                progress?.let { it(componentToResolve, resolved, total) }
            }
            component.components.clear()
            component.components.addAll(resolvedComponents)
        } else {
            resolveDependencies(component)
        }
    }

    override fun upload(component: Component, progress: UploadProcessCallback?) {
        val artifacts = if (component is ComplexComponent) {
            component.components.stream()
                .flatMap { it.dependencies.stream() }
                .collect(Collectors.toSet())
        } else {
            component.dependencies
        }

        val total = artifacts.size
        var uploaded = 0

        artifacts.parallelStream().forEach { artifact ->
            mvnArtifactManager.upload(artifact)
            uploaded++
            progress?.let { it(artifact, uploaded, total) }
        }
    }

    override fun register(component: Component) {
        metadataHolder.getMetadata().components.add(component)
//        if (component is ComplexComponent) {
//            component.components.forEach {
//                metadataHolder.getMetadata().components.add(it)
//            }
//        }
        metadataHolder.flushMetadata()
    }

    private fun resolveDependencies(component: Component): Component? {
        if (component.name != null) {
            val artifact = MvnArtifact(component.packageName, component.name, component.version)
            val dependencies = mvnArtifactManager.findDependencies(artifact) ?: return null

            component.dependencies.add(artifact)
            component.dependencies.addAll(dependencies)

            mvnArtifactManager.downloadWithDependencies(artifact)

            component.dependencies.parallelStream().forEach {
                mvnArtifactManager.resolveClassifiers(it)
            }


            return component
        }
        return null
    }

    private fun searchBintray(context: SearchContext): ComplexComponent? {
        val (_, _, result) = sdkSettings.getProperty("search-url")
            .httpGet(
                listOf(
                    "g" to context.componentPackage,
                    "a" to "*",
                    "subject" to sdkSettings.getProperty("search-subject")
                )
            )
            .header(Headers.CONTENT_TYPE, "application/json")
            .header(Headers.ACCEPT, "application/json")
            .header(Headers.CACHE_CONTROL, "no-cache")
            .responseJson()

        result.fold(
            success = {
                val array = it.array()
                if (array.isEmpty) {
                    throw IllegalStateException("Unknown framework: ${context.componentPackage}")
                }
                val json = array.get(0) as JSONObject
                val versions = json.get("versions") as JSONArray
                if (!versions.contains(context.version)) {
                    throw IllegalStateException("Unknown version: ${context.version}")
                }

                val systemIds = json.get("system_ids") as JSONArray
                return ComplexComponent(
                    packageName = context.componentPackage,
                    name = context.name,
                    version = context.version,
                    type = context.type,
                    components = systemIds.toList().stream()
                        .map { it as String }
                        .map {
                            val split = it.split(":")
                            return@map Component(split[0], split[1], context.version)
                        }.collect(Collectors.toList())
                )
            },
            failure = { error ->
                throw IllegalStateException("Framework request error: $error")
            }
        )
    }
}