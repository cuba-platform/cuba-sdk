package com.haulmont.cuba.cli.plugin.sdk.commands.repository

import com.beust.jcommander.Parameters
import com.haulmont.cli.core.green
import com.haulmont.cli.core.prompting.Answers
import com.haulmont.cli.core.prompting.Prompts
import com.haulmont.cli.core.prompting.QuestionsList
import com.haulmont.cuba.cli.plugin.sdk.commands.AbstractSdkCommand
import com.haulmont.cuba.cli.plugin.sdk.di.sdkKodein
import com.haulmont.cuba.cli.plugin.sdk.dto.Repository
import com.haulmont.cuba.cli.plugin.sdk.dto.RepositoryTarget
import com.haulmont.cuba.cli.plugin.sdk.dto.RepositoryType
import com.haulmont.cuba.cli.plugin.sdk.event.SdkEvent
import com.haulmont.cuba.cli.plugin.sdk.services.ArtifactManager
import com.haulmont.cuba.cli.plugin.sdk.services.RepositoryManager
import org.kodein.di.generic.instance
import java.nio.file.Files
import java.nio.file.Path

@Parameters(commandDescription = "Init SDK")
class InitCommand : AbstractSdkCommand() {

    private val artifactManager: ArtifactManager by lazy { ArtifactManager.instance() }

    private val repositoryManager: RepositoryManager by sdkKodein.instance<RepositoryManager>()

    override fun onlyForConfiguredSdk(): Boolean = false

    override fun run() {
        Prompts.create(kodein) { askInitSettings() }
            .let(Prompts::ask)
            .let(this::init)
    }

    private fun QuestionsList.askInitSettings() {
        question("sdk-home", messages["init.sdk-home"]) {
            default(sdkSettings.sdkHome().toString())
        }
    }

    private fun init(answers: Answers) {
        val sdkHome = Path.of(answers["sdk-home"] as String)
        createSdkDir(sdkHome)
        createSdkRepoSettingsFile(sdkHome)

        configureArtifactManager()
        initLocalMavenRepo()
        bus.post(SdkEvent.SdkInitEvent())
        printWriter.println(messages["setup.sdkConfigured"].green())
    }

    private fun initLocalMavenRepo() {
        val m2Path =
            if (sdkSettings.hasProperty("maven.local.repo"))
                Path.of(sdkSettings["maven.local.repo"])
            else
                sdkSettings.sdkHome().resolve("m2")
        val sdkLocalRepo = m2Path.apply {
            if (!Files.exists(this)) {
                Files.createDirectories(this)
            }
        }
        val repository = Repository(
            name = "sdk-local",
            type = RepositoryType.LOCAL,
            url = sdkLocalRepo.toString()
        )
        repositoryManager.removeRepository(repository.name, RepositoryTarget.SOURCE)
        repositoryManager.addRepository(repository.copy(), RepositoryTarget.SOURCE)
    }

    private fun configureArtifactManager() {
        artifactManager.init()
    }

    private fun createSdkRepoSettingsFile(sdkHome: Path) {
        sdkSettings["sdk.home"] = sdkHome.toString()
        sdkSettings["sdk.export.path"] = sdkHome.resolve("export").toString()
        sdkSettings["sdk.files"] = sdkHome.resolve("files").toString()
        if (!sdkSettings.hasProperty("repository.type")) {
            sdkSettings["repository.type"] = "none"
        }
        sdkSettings.flushAppProperties()
    }

    private fun createSdkDir(sdkHome: Path) {
        if (!Files.exists(sdkHome)) {
            Files.createDirectories(sdkHome)
        }
    }
}

