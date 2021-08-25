package com.haulmont.cli.plugin.sdk.component.cuba.providers

import com.haulmont.cli.plugin.sdk.component.cuba.di.cubaComponentKodein
import com.haulmont.cli.plugin.sdk.component.cuba.dto.JmixComponent
import com.haulmont.cuba.cli.plugin.sdk.commands.artifacts.NameVersion
import com.haulmont.cuba.cli.plugin.sdk.dto.Classifier
import com.haulmont.cuba.cli.plugin.sdk.dto.Classifier.Companion.pom
import com.haulmont.cuba.cli.plugin.sdk.dto.Component
import com.haulmont.cuba.cli.plugin.sdk.dto.MvnArtifact
import com.haulmont.cuba.cli.plugin.sdk.services.SdkSettingsHolder
import org.kodein.di.generic.instance

class JmixFrameworkProvider : JmixProvider() {
    internal val sdkSettings: SdkSettingsHolder by cubaComponentKodein.instance<SdkSettingsHolder>()

    companion object {
        const val JMIX_PLATFORM_PROVIDER = "jmix"
    }

    override fun getType() = JMIX_PLATFORM_PROVIDER;

    override fun getName() = "jmix-core-starter"

    override fun versions(componentId: String?) = super.versions("io.jmix.core:jmix-core")

    override fun resolveCoordinates(nameVersion: NameVersion): Component? {
        nameVersion.split(":").let {
            when (it.size) {
                1 -> return JmixComponent(
                    groupId = "io.jmix.core",
                    artifactId = "jmix-core-starter",
                    version = it[0],
                    type = getType(),
                    name = getName()
                )
                2 -> return JmixComponent(
                    groupId = "io.jmix.core",
                    artifactId = "jmix-core-starter",
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
            "io.jmix.core",
            "jmix-core-starter",
            template.version,
            type = getType(),
            id = "jmix",
            name = "jmix-core-starter",
            frameworkVersion = template.version,
            components = defaultComponents("io.jmix", JMIX_PLATFORM_PROVIDER, template.version)
        ).apply {
            components.addAll(additionalPlatformLibs(template))
            components.addAll(sdkBomDependencies(template))
        })

    private fun sdkBomDependencies(template: Component): MutableList<Component> {
        val sdkBomDependencies = mutableListOf<Component>()
        val model = artifactManager.readPom(
            MvnArtifact("io.jmix.build", "io.jmix.build.gradle.plugin", template.version),
            Classifier.pom()
        )
        if (model != null) {
            val gradleVersion = model.properties["gradle.version"] as String?
            if (gradleVersion != null) {
                sdkBomDependencies.add(
                    Component(
                        groupId = "gradle",
                        artifactId = "gradle",
                        url = sdkSettings["gradle.downloadLink"].format(gradleVersion),
                        version = gradleVersion,
                        classifiers = mutableSetOf(Classifier("", "zip"))
                    )
                )
            }
            model.dependencies.forEach {
                sdkBomDependencies.add(Component(it.groupId, it.artifactId, it.version))
            }
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
            Component("org.springframework.boot", "spring-boot-starter-web", "2.5.2")
        )
    }

    private fun defaultComponents(packageName: String, name: String, version: String): MutableSet<Component> {
        return mutableSetOf(
            Component("$packageName.data", "$name-eclipselink-starter", version),
            Component("$packageName.ui", "$name-jmix-ui-starter", version),
            Component("$packageName.ui", "$name-ui-data-starter", version),
            Component("$packageName.ui", "$name-ui-themes-compiled", version),
            Component("$packageName.ui", "$name-ui-widgets-compiled", version),
            Component("$packageName.security", "$name-security-starter", version),
            Component("$packageName.security", "$name-security-ui-starter", version),
            Component("$packageName.security", "$name-security-data-starter", version),
            Component("$packageName.localfs", "$name-localfs-starter", version)
        )
    }
}