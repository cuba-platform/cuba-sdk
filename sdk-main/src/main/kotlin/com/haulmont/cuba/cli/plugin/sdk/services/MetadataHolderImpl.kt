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
import com.haulmont.cuba.cli.plugin.sdk.di.sdkKodein
import com.haulmont.cuba.cli.plugin.sdk.dto.Component
import org.kodein.di.generic.instance

class MetadataHolderImpl : MetadataHolder {

    private val dbProvider: DbProvider by sdkKodein.instance<DbProvider>()

    private fun dbInstance() = dbProvider.get("metadata")

    private val resolvedComponents by lazy {
        if (dbProvider.dbExists("metadata")) {
            val resolved = mutableSetOf<Component>()
            dbInstance().map("resolved").forEach {
                val json = it.value
                if (json != null) {
                    val component = Gson().fromJson(json, Component::class.java)
                    resolved.add(component)
                }
            }
            return@lazy resolved
        } else {
            return@lazy mutableSetOf<Component>()
        }
    }

    private val installedComponents by lazy {
        if (dbProvider.dbExists("metadata")) {
            val resolved = mutableSetOf<Component>()
            dbInstance().map("installed").forEach {
                val json = it.value
                if (json != null) {
                    val component = Gson().fromJson(json, Component::class.java)
                    resolved.add(component)
                }
            }
            return@lazy resolved
        } else {
            return@lazy mutableSetOf<Component>()
        }
    }

    override fun getResolved(): Set<Component> {
        return resolvedComponents
    }

    override fun getInstalled(): Set<Component> {
        return installedComponents
    }

    override fun addResolved(component: Component) {
        resolvedComponents.add(component)
        dbInstance().set("resolved", component.toString(), Gson().toJson(component))
    }

    override fun addInstalled(component: Component) {
        installedComponents.add(component)
        dbInstance().set("installed", component.toString(), Gson().toJson(component))
    }

    override fun removeResolved(component: Component) {
        val resolvedComponent = resolvedComponents.firstOrNull { it.isSame(component) }
        if (resolvedComponent != null) {
            resolvedComponents.remove(resolvedComponent)
            dbInstance().remove("resolved", resolvedComponent.toString())
        }
    }

    override fun removeInstalled(component: Component) {
        val installedComponent = installedComponents.firstOrNull { it.isSame(component) }
        if (installedComponent != null) {
            installedComponents.remove(installedComponent)
            dbInstance().remove("installed", installedComponent.toString())
        }
    }
}