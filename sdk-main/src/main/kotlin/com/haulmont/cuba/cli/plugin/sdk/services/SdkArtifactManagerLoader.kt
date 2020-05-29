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

package com.haulmont.cuba.cli.plugin.sdk.services

import com.google.common.eventbus.EventBus
import com.haulmont.cli.core.CliContext
import com.haulmont.cli.core.PluginLoader
import com.haulmont.cli.core.bgRed
import com.haulmont.cli.core.kodein
import com.haulmont.cuba.cli.plugin.sdk.di.sdkKodein
import org.kodein.di.generic.instance
import java.io.PrintWriter
import java.lang.module.ModuleFinder
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.logging.Level
import java.util.logging.Logger

class SdkArtifactManagerLoader {

    private val log: Logger = Logger.getLogger(PluginLoader::class.java.name)

    private val writer: PrintWriter by kodein.instance<PrintWriter>()

    private val context: CliContext by kodein.instance<CliContext>()sdk

    private val sdkSettings: SdkSettingsHolder by sdkKodein.instance<SdkSettingsHolder>()

    private val bus: EventBus by kodein.instance<EventBus>()

    fun instance(): ArtifactManager? {
        val managers = loadClassImplFromPlugins(ArtifactManager::class.java)
        val resolver = managers.firstOrNull { it.name == sdkSettings["artifact.resolver"] }
        if (resolver != null) {
            bus.register(resolver)
        }
        return resolver
    }

    fun instances(): Collection<ArtifactManager> {
        return loadClassImplFromPlugins(ArtifactManager::class.java)
    }

    private fun walkDirectory(rootDir: Path, action: (dir: Path) -> Unit) {
        if (Files.exists(rootDir)) {
            action(rootDir)
            Files.walk(rootDir, 1)
                .filter { it != rootDir }
                .filter { Files.isDirectory(it) }
                .forEach { action(it) }
        }
    }

    fun <T> loadClassImplFromPlugins(clazz: Class<T>): Collection<T> {
        val classImpls = linkedSetOf<T>()
        PluginLoader().systemPluginsPaths().forEach { pluginsDir ->
            walkDirectory(pluginsDir) {
                classImpls.addAll(findClassImpls(pluginsDir, clazz))
            }
        }
        context.mainPlugin()?.pluginsDir?.let { pluginsDir ->
            walkDirectory(pluginsDir) {
                classImpls.addAll(findClassImpls(pluginsDir, clazz))
            }
        }
        return classImpls
    }

    private fun <T> findClassImpls(pluginsDir: Path, clazz: Class<T>): Collection<T> {
        createModuleLayer(pluginsDir)?.let {
            return ServiceLoader.load(it, clazz).toList()
        } ?: return emptyList()
    }

    private fun createModuleLayer(pluginsDir: Path): ModuleLayer? = try {
        val bootLayer = ModuleLayer.boot()

        val pluginModulesFinder = ModuleFinder.of(pluginsDir)
        val pluginModules = pluginModulesFinder.findAll().map {
            it.descriptor().name()
        }

        val configuration = bootLayer.configuration().resolve(pluginModulesFinder, ModuleFinder.of(), pluginModules)

        ModuleLayer.defineModulesWithOneLoader(
            configuration,
            mutableListOf(bootLayer),
            ClassLoader.getSystemClassLoader()
        ).layer()
    } catch (e: Exception) {
        log.log(Level.WARNING, "Error during loading module layer from directory $pluginsDir", e)
        writer.println("Error during loading module layer from directory $pluginsDir".bgRed())
        null
    }
}