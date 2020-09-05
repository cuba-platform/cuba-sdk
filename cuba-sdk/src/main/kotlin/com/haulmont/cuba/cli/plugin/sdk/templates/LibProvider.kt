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

import com.haulmont.cuba.cli.plugin.sdk.commands.artifacts.NameVersion
import com.haulmont.cuba.cli.plugin.sdk.dto.Classifier
import com.haulmont.cuba.cli.plugin.sdk.dto.Component

open class LibProvider : BintraySearchComponentProvider() {

    override fun getType() = "lib"

    override fun getName() = "Library"

    override fun createFromTemplate(template: Component): Component {
        return Component(
            id = "${template.groupId}:${template.artifactId}",
            groupId = template.groupId,
            artifactId = template.artifactId,
            version = template.version,
            type = getType()
        )
    }

    override fun components(): List<Component>? = emptyList()

    override fun resolveCoordinates(nameVersion: NameVersion): Component? =
        nameVersion.split(":").let {
            when (it.size) {
                3 -> return Component(it[0], it[1], it[2], type = getType())
                4 -> return Component(
                    it[0],
                    it[1],
                    it[2],
                    type = getType(),
                    classifiers = mutableSetOf(Classifier.pom(), Classifier(it[3]))
                )
                5 -> return Component(
                    it[0],
                    it[1],
                    it[2],
                    type = getType(),
                    classifiers = mutableSetOf(Classifier.pom(), Classifier(it[3], it[4]))
                )
                else -> return null
            }

        }

    override fun load() {
    }

}