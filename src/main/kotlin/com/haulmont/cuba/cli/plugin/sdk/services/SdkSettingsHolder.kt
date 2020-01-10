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

import java.nio.file.Path

interface SdkSettingsHolder {

    val sdkHome: Path

    fun getProperty(property: String): String

    fun hasProperty(property: String): Boolean

    fun setProperty(property: String, value: String)

    fun flushAppProperties()

    fun sdkConfigured(): Boolean

    operator fun get(property: String): String = getProperty(property)
    operator fun set(property: String, value: String) = setProperty(property, value)
}