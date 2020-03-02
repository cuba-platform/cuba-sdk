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

import com.haulmont.cuba.cli.plugin.sdk.dto.Classifier
import com.haulmont.cuba.cli.plugin.sdk.dto.Component
import com.haulmont.cuba.cli.plugin.sdk.dto.MvnArtifact
import com.haulmont.cuba.cli.plugin.sdk.dto.Repository
import org.apache.maven.model.Model
import java.nio.file.Path

interface ArtifactManager {

    fun init()

    fun uploadComponentToLocalCache(component: Component): List<MvnArtifact>

    fun readPom(artifact: MvnArtifact, classifier: Classifier = Classifier.pom()): Model?

    fun upload(repositories: List<Repository>, artifact: MvnArtifact)

    fun getArtifact(artifact: MvnArtifact, classifier: Classifier)

    fun getOrDownloadArtifactFile(artifact: MvnArtifact, classifier: Classifier): Path

    fun getOrDownloadArtifactWithClassifiers(artifact: MvnArtifact, classifiers:Collection<Classifier>)

    fun resolve(artifact: MvnArtifact, classifier: Classifier = Classifier.default()): List<MvnArtifact>

    fun checkClassifiers(artifact: MvnArtifact)

    fun remove(artifact: MvnArtifact)
}