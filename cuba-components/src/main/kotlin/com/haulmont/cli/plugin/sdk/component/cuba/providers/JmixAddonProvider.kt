package com.haulmont.cli.plugin.sdk.component.cuba.providers

import com.haulmont.cli.core.localMessages
import com.haulmont.cli.core.prompting.Option
import com.haulmont.cli.plugin.sdk.component.cuba.di.cubaComponentKodein
import com.haulmont.cli.plugin.sdk.component.cuba.dto.JmixComponent
import com.haulmont.cli.plugin.sdk.component.cuba.services.jmix.JmixComponentVersionManager
import com.haulmont.cuba.cli.plugin.sdk.commands.artifacts.NameVersion
import com.haulmont.cuba.cli.plugin.sdk.dto.Component
import com.haulmont.cuba.cli.plugin.sdk.dto.JmixMarketplaceAddon
import com.haulmont.cuba.cli.plugin.sdk.dto.MvnArtifact
import org.apache.maven.model.Dependency
import org.kodein.di.generic.instance
import java.util.logging.Logger

class JmixAddonProvider : JmixProvider() {

    internal val componentVersionsManager: JmixComponentVersionManager by cubaComponentKodein.instance<JmixComponentVersionManager>()

    internal val messages by localMessages()

    private val log: Logger = Logger.getLogger(JmixAddonProvider::class.java.name)

    override fun getType() = "jmix-addon"

    override fun getName() = "Jmix addon"

    override fun createFromTemplate(template: Component): Component? {
        val mAddon = searchInMarketplace(
            id = template.id, groupId = template.groupId, artifactId = template.artifactId
        )?.let { initAddonTemplate(it, template.version) }
        return search(
            mAddon ?: JmixComponent(template.groupId,
                template.artifactId,
                template.version,
                id = template.id,
                name = template.name,
                type = getType(),
                components = addonComponents(template.groupId, template.artifactId, template.version))
        )
    }

    override fun components() = componentVersionsManager
        .addons()
        .sortedBy { it.id }
        .map { initAddonTemplate(it, "\${version}") }
        .toList()

    private fun initAddonTemplate(addon: JmixMarketplaceAddon, version: String): Component {
        val artifact = addon.dependencies[0]
        val packageName = artifact.group
        val name = artifact.name
        val componentName = addon.id
        return JmixComponent(
            groupId = packageName,
            artifactId = name,
            version = version,
            type = getType(),
            id = addon.id,
            name = addon.name,
            description = addon.description,
            category = addon.category,
            components = addonComponents(packageName, name, version)
        )
//            .apply {
//            when (componentName) {
////                "bpm" -> this.components.add(Component(packageName, "$name-modeler", version))
//                "maps" -> this.components.add(Component(packageName, "$name-ui-widgets", version))
//            }
//        }
    }

    private fun addonComponents(packageName: String, name: String, version: String): MutableSet<Component> {
        return mutableSetOf(
            Component("${packageName}.core", "$name-core-starter", version),
            Component("${packageName}.data", "$name-eclipselink-starter", version),
            Component("${packageName}.ui", "$name-ui-starter", version),
            Component("${packageName}.ui", "$name-ui-themes", version)
        )
    }

    override fun versions(componentId: String?) = componentVersionsManager.addons()
        .filter {
            it.id == componentId
        }
        .flatMap { it.compatibilityList }
        .flatMap {
            it.artifactVersions.map { version ->
                Option(
                    version,
                    "%s [Jmix %s]".format(version, it.platformRequirement),
                    version
                )
            }
        }
        .sortedByDescending { it.value }
        .toList()

    override fun resolveCoordinates(nameVersion: NameVersion): Component? {
        nameVersion.split(":").let {
            when (it.size) {
                3 -> {
                    val mAddon = searchInMarketplace(groupId = it[0], artifactId = it[1])
                    if (mAddon != null) {
                        return initAddonTemplate(mAddon, it[2])
                    } else {
                        return JmixComponent(
                            groupId = it[0],
                            artifactId = it[1],
                            version = it[2],
                            type = getType()
                        )
                    }
                }
                2 -> {
                    val mAddon = searchInMarketplace(it[0])
                    if (mAddon != null) {
                        return initAddonTemplate(mAddon, it[1])
                    }
                    return null
                }
                else -> return null
            }
        }
    }

    private fun searchInMarketplace(id: String? = null, groupId: String? = null, artifactId: String? = null) =
        componentVersionsManager.addons()
            .find { addon ->
                val artifact = addon.dependencies[0]
                addon.id == id || (artifact.group == groupId && artifact.name == artifactId)
                            || (artifact.group == groupId && artifact.name == "$artifactId")
            }

    override fun searchAdditionalComponents(component: Component): Set<Component> {
        val additionalComponentList = mutableSetOf<Component>()
        (component as JmixComponent).let { global ->
            val model = artifactManager.readPom(
                MvnArtifact(
                    global.groupId,
                    global.artifactId,
                    global.version
                )
            )
            if (model == null) {
                log.info("Component not found: ${component}")
                throw IllegalStateException("Component not found: ${component}")
            }
            component.frameworkVersion =
                model.dependencies.filter { it.artifactId == "jmix-core-starter" }.map { it.version }.firstOrNull()
            model.dependencies.filter { it.artifactId.endsWith("-starter") }
                .forEach {
                        jmixAddon(it)?.let {
                            additionalComponentList.add(it)
                            additionalComponentList.addAll(searchAdditionalComponents(it))
                        }
                }
        }
        return additionalComponentList
    }

    private fun jmixAddon(dependency: Dependency): Component? {
        return createFromTemplate(
            Component(
                dependency.groupId,
                dependency.artifactId,
                dependency.version
            )
        )
    }

    override fun load() {
        componentVersionsManager.load { }
    }
}