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

import com.haulmont.cli.core.prompting.Option
import com.haulmont.cuba.cli.plugin.sdk.commands.artifacts.NameVersion
import com.haulmont.cuba.cli.plugin.sdk.dto.Component

/** Component provider interface. Component providers should be registered in ComponentRegistry */
interface ComponentProvider {

    /**
     * Returns provider human readable name
     */
    fun getName(): String

    /**
     * Returns provider type, provider type should be unique
     */
    fun getType(): String

    /**
     * Create final component to resolve from provided component template
     * @param template provided component template
     */
    fun createFromTemplate(template: Component): Component?

    /**
     * Provides list of available provider components,
     * if method return null then component provided provides only one component
     */
    fun components(): List<Component>? = null

    /**
     * Provides list of available component version by component id
     * @param componentId componentId
     */
    fun versions(componentId: String?): List<Option<String>> = emptyList()

    /**
     * Create component template from provided string coordinates
     * @param nameVersion provided component coordinates
     */
    fun resolveCoordinates(nameVersion: NameVersion): Component?

    /**
     * Provides additional components which should be resolved with provided component
     * @param component provided component
     */
    fun searchAdditionalComponents(component: Component): Set<Component> = emptySet()

    /**
     * Load provider metadata if required
     */
    fun load()

}