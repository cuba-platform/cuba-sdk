package com.haulmont.cli.plugin.sdk.component.cuba.providers

import com.haulmont.cli.plugin.sdk.component.cuba.dto.JmixComponent
import com.haulmont.cuba.cli.plugin.sdk.commands.artifacts.NameVersion
import com.haulmont.cuba.cli.plugin.sdk.dto.Classifier.Companion.pom
import com.haulmont.cuba.cli.plugin.sdk.dto.Component
import com.haulmont.cuba.cli.plugin.sdk.dto.MvnArtifact

class JmixFrameworkProvider : JmixProvider() {

    companion object {
        const val JMIX_PLATFORM_PROVIDER = "jmix"
    }

    private val defaultJmixModuleDependencies = setOf(
        buildStarterArtifactId("core"),
        buildStarterArtifactId("eclipselink"),
        buildStarterArtifactId("ui"),
        buildStarterArtifactId("ui-data"),
        "jmix-ui-themes-compiled",
        "jmix-ui-widgets-compiled",
        buildStarterArtifactId("security"),
        buildStarterArtifactId("security-ui"),
        buildStarterArtifactId("security-data"),
        buildStarterArtifactId("localfs"),
        buildStarterArtifactId("datatools"),
        buildStarterArtifactId("datatools-ui")
    )

    override fun getType() = JMIX_PLATFORM_PROVIDER;

    override fun getName() = "jmix"

    override fun versions(componentId: String?) = super.versions("io.jmix.bom:jmix-bom")

    override fun resolveCoordinates(nameVersion: NameVersion): Component? {
        nameVersion.split(":").let {
            when (it.size) {
                1 -> return JmixComponent(
                    groupId = "io.jmix",
                    artifactId = "jmix",
                    version = it[0],
                    type = getType(),
                    name = getName()
                )
                2 -> return JmixComponent(
                    groupId = "io.jmix",
                    artifactId = "jmix",
                    version = it[1],
                    type = getType(),
                    name = getName()
                )
                else -> return null
            }
        }
    }

    override fun load() {

    }

    override fun createFromTemplate(template: Component) = search(
        JmixComponent(
            "io.jmix",
            "jmix",
            template.version,
            type = getType(),
            id = "jmix",
            name = "Jmix",
            frameworkVersion = template.version,
            components = defaultComponents(template.version)
        ).apply {
            components.addAll(additionalPlatformLibs(template))
            components.addAll(sdkBomDependencies(template))
        })

    private fun sdkBomDependencies(template: Component): MutableList<Component> {
        val sdkBomDependencies = mutableListOf<Component>()
        val model = artifactManager.readPom(
            MvnArtifact("io.jmix.build", "io.jmix.build.gradle.plugin", template.version),
            pom()
        )
        model?.dependencies?.forEach {
            sdkBomDependencies.add(Component(it.groupId, it.artifactId, it.version))
        }
        return sdkBomDependencies
    }

    private fun additionalPlatformLibs(template: Component): MutableSet<Component> {
        return mutableSetOf(
            Component(
                "io.jmix.build", "io.jmix.build.gradle.plugin", template.version, classifiers =
                mutableSetOf(pom())
            ),
            Component("org.hsqldb", "hsqldb", "2.4.1"),

            Component("javax.validation", "validation-api", "1.0.0.GA"), // Used by Vaadin Widgetset Compilation
            Component("org.jsoup", "jsoup", "1.14.3"),
            Component("javax.xml.bind", "jaxb-api", "2.3.1"),
            Component("org.jboss.logging", "jboss-logging", "3.4.3.Final"),
            Component("org.jetbrains.kotlin", "kotlin-bom", "1.5.10"),

//            Component("org.springframework.boot", "spring-boot-starter-test", "2.5.2")
        )
    }

    private fun defaultComponents(version: String): MutableSet<Component> {
        val packageName = "io.jmix"
        val name = "jmix"

        val baseComponents = mutableSetOf(
            Component("$packageName.bom", "$name-bom", version, mutableSetOf(pom()))
        )

        val model = artifactManager.readPom(
            MvnArtifact("io.jmix.bom", "jmix-bom", version)
        )

        if (model != null) {
            val dependencies = model.dependencyManagement.dependencies
            dependencies.forEach { dependency ->
                if (defaultJmixModuleDependencies.contains(dependency.artifactId)) {
                    baseComponents.add(Component(dependency.groupId, dependency.artifactId, dependency.version))
                }
                if (dependency.groupId == "org.springframework.boot" &&
                    dependency.artifactId == "spring-boot-dependencies"
                ) {
                    baseComponents.addAll(buildSpringComponents(dependency.version))
                }
            }
        }

        return baseComponents
    }

    private fun buildStarterArtifactId(jmixModule: String): String {
        return "jmix-$jmixModule-starter"
    }

    private fun getSpringFrameworkVersion(springBootVersion: String): String {
        val model = artifactManager.readPom(
            MvnArtifact("org.springframework.boot", "spring-boot-dependencies", springBootVersion),
            pom()
        )

        return model?.properties?.get("spring-framework.version").toString()
    }

    private fun buildSpringComponents(springBootVersion: String): Set<Component> {
        val springFrameworkVersion = getSpringFrameworkVersion(springBootVersion)

        return setOf(
            Component(
                "org.springframework.boot",
                "spring-boot-starter-web",
                springBootVersion
            ),
            Component(
                "org.springframework",
                "spring-orm",
                springFrameworkVersion
            ),
            Component(
                "org.springframework",
                "spring-messaging",
                springFrameworkVersion
            )
        )
    }
}