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

package com.haulmont.cuba.cli.plugin.sdk.commands.artifacts

import com.haulmont.cli.core.green
import com.haulmont.cli.core.red

abstract class BaseResolveCommand : BaseComponentCommand() {

    override fun run() {
        createSearchContext()?.let {
            printWriter.println(messages["resolve.start"].format(it))
            var component = searchInMetadata(it)

            if (force(it) || component == null) {
                component = search(it)
            } else {
                printWriter.println(messages["resolve.alreadyResolved"].green())
                return
            }
            component?.let {
                resolve(componentWithDependents(component))
            } ?: printWriter.println(messages["resolve.notFound"].format(it).red())
        }
    }
}