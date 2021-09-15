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

package com.haulmont.cuba.cli.plugin.sdk.dto

data class CubaMarketplaceAddon(
    val id: String,
    val name: String,
    val about: String,
    val description: String,
    val category: String,
    val tags: List<String>,
    val vendor: String,
    val updateDateTime: Long,
    val groupId: String,
    val artifactId: String,
    var compatibilityList: List<MarketplaceAddonCompatibility>,
    val commercial: Boolean
)