package com.haulmont.cli.plugin.sdk.component.cuba.dto

import com.haulmont.cuba.cli.plugin.sdk.dto.Classifier
import com.haulmont.cuba.cli.plugin.sdk.dto.Component
import com.haulmont.cuba.cli.plugin.sdk.dto.MvnArtifact

class JmixComponent(
    groupId: String,
    artifactId: String,
    version: String,
    classifiers: MutableSet<Classifier> = mutableSetOf(
        Classifier.jar(),
        Classifier.pom(),
        Classifier.sources()
    ),

    id: String? = null,
    type: String = "",
    name: String? = null,
    description: String? = null,
    category: String? = null,
    var frameworkVersion: String? = null,

    url: String? = null,

    components: MutableSet<Component> = HashSet(),
    dependencies: MutableSet<MvnArtifact> = HashSet()
) : Component(
    groupId,
    artifactId,
    version,
    classifiers,

    id,
    type,
    name,
    description,
    category,

    url,

    components,
    dependencies
) {
    fun starterModule() =
        components.firstOrNull { it.artifactId.endsWith("-starter") }
}