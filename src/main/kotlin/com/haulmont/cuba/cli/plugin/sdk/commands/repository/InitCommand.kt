package com.haulmont.cuba.cli.plugin.sdk.commands.repository

import com.beust.jcommander.Parameters
import com.haulmont.cuba.cli.cubaplugin.di.sdkKodein
import com.haulmont.cuba.cli.green
import com.haulmont.cuba.cli.plugin.sdk.commands.AbstractSdkCommand
import com.haulmont.cuba.cli.plugin.sdk.dto.Repository
import com.haulmont.cuba.cli.plugin.sdk.dto.RepositoryTarget
import com.haulmont.cuba.cli.plugin.sdk.dto.RepositoryType
import com.haulmont.cuba.cli.plugin.sdk.gradle.GradleConnector
import com.haulmont.cuba.cli.plugin.sdk.services.ArtifactManager
import com.haulmont.cuba.cli.plugin.sdk.services.RepositoryManager
import org.kodein.di.generic.instance
import java.nio.file.Files
import java.nio.file.Path
import kotlin.concurrent.thread


@Parameters(commandDescription = "Init SDK")
class InitCommand : AbstractSdkCommand() {

    private val artifactManager: ArtifactManager by sdkKodein.instance<ArtifactManager>()

    private val repositoryManager: RepositoryManager by sdkKodein.instance<RepositoryManager>()

    override fun onlyForConfiguredSdk(): Boolean = false

    override fun run() {
        init();
    }

    private fun init() {
        createSdkDir()
        createSdkRepoSettingsFile()
        downloadAndConfigureGradle()
        initLocalMavenRepo()
        printWriter.println(messages["setup.sdkConfigured"].green())
    }

    private fun initLocalMavenRepo() {
        val sdkLocalRepo = sdkSettings.sdkHome().resolve("m2").apply {
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
        repositoryManager.removeRepository(repository.name, RepositoryTarget.SEARCH)

        repositoryManager.addRepository(repository.copy(), RepositoryTarget.SOURCE)
        repositoryManager.addRepository(repository.copy(), RepositoryTarget.SEARCH)

    }

    private fun downloadAndConfigureGradle() {
        artifactManager.init()
        val thread = thread {
            GradleConnector().runTask("wrapper")
        }
        waitTask(messages["setup.downloadGradle"]) {
            thread.isAlive
        }
    }

    private fun createSdkRepoSettingsFile() {
        sdkSettings["sdk.home"] = sdkSettings.sdkHome().toString()
        sdkSettings["sdk.export.path"] = sdkSettings.sdkHome().resolve("export").toString()
        sdkSettings["sdk.files"] = sdkSettings.sdkHome().resolve("files").toString()
        sdkSettings["gradle.home"] = sdkSettings.sdkHome().resolve("gradle").toString()
        sdkSettings["gradle.cache"] = sdkSettings.sdkHome().resolve(Path.of("gradle", "cache")).toString()
        if (!sdkSettings.hasProperty("repository.type")) {
            sdkSettings["repository.type"] = "none"
        }
        sdkSettings.flushAppProperties()
    }

    private fun createSdkDir() {
        if (!Files.exists(sdkSettings.sdkHome())) {
            Files.createDirectories(sdkSettings.sdkHome())
        }
    }
}

