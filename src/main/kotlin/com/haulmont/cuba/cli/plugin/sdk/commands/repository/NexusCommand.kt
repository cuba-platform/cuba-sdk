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

package com.haulmont.cuba.cli.plugin.sdk.commands.repository

import com.github.kittinunf.fuel.httpHead
import com.haulmont.cuba.cli.cubaplugin.di.sdkKodein
import com.haulmont.cuba.cli.plugin.sdk.commands.AbstractSdkCommand
import com.haulmont.cuba.cli.plugin.sdk.nexus.NexusManager
import org.kodein.di.generic.instance

abstract class NexusCommand : AbstractSdkCommand() {

    internal val nexusManager: NexusManager by sdkKodein.instance()

    internal fun repositoryStarted(): Boolean {
        val (_, response, _) = sdkSettings["repository.url"]
            .httpHead()
            .response()
        return response.statusCode == 200
    }
}