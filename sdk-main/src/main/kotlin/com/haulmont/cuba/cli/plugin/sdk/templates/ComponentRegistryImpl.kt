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

package com.haulmont.cuba.cli.plugin.sdk.templates

import com.google.common.eventbus.EventBus
import com.haulmont.cli.core.kodein
import org.kodein.di.generic.instance

class ComponentRegistryImpl : ComponentRegistry {

    private val bus: EventBus by kodein.instance<EventBus>()

    val providersMap = mutableMapOf<String, ComponentProvider>()

    override fun addProviders(vararg providers: ComponentProvider) {
        for (provider in providers){
            providersMap[provider.getType()] = provider
            bus.register(provider)
        }
    }

    override fun providers(): Collection<ComponentProvider> {
        return providersMap.values
    }

    override fun providerByName(name: String): ComponentProvider {
        if (providersMap.containsKey(name)) {
            return providersMap[name]!!
        } else {
            throw IllegalStateException("Unknown component provider $name")
        }
    }
}