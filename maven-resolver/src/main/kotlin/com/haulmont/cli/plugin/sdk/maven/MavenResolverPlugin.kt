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

package com.haulmont.cli.plugin.sdk.maven

import com.google.common.eventbus.Subscribe
import com.haulmont.cli.core.API_VERSION
import com.haulmont.cli.core.CliPlugin
import com.haulmont.cli.core.event.DestroyPluginEvent
import com.haulmont.cli.core.event.InitPluginEvent
import com.haulmont.cli.plugin.sdk.maven.di.mavenSdkKodein
import com.haulmont.cuba.cli.plugin.sdk.di.sdkKodein
import com.haulmont.cuba.cli.plugin.sdk.services.SdkSettingsHolder
import org.kodein.di.generic.instance


class MavenResolverPlugin : CliPlugin {
    override val apiVersion = API_VERSION

    internal val mavenExecutor: MavenExecutor by mavenSdkKodein.instance<MavenExecutor>()
    internal val sdkSettings: SdkSettingsHolder by sdkKodein.instance<SdkSettingsHolder>()

    @Subscribe
    fun onInit(event: InitPluginEvent) {

    }

    @Subscribe
    fun onDestroy(event: DestroyPluginEvent) {
    }


}