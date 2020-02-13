package com.haulmont.cuba.cli.plugin.sdk.commands.repository

import com.beust.jcommander.Parameters
import com.haulmont.cuba.cli.cubaplugin.di.sdkKodein
import com.haulmont.cuba.cli.green
import com.haulmont.cuba.cli.plugin.sdk.commands.AbstractSdkCommand
import com.haulmont.cuba.cli.plugin.sdk.gradle.GradleConnector
import com.haulmont.cuba.cli.plugin.sdk.services.ArtifactManager
import org.kodein.di.generic.instance
import java.nio.file.Files
import java.nio.file.Path
import kotlin.concurrent.thread


@Parameters(commandDescription = "Init SDK")
class InitCommand : AbstractSdkCommand() {

    private val artifactManager: ArtifactManager by sdkKodein.instance()

    override fun onlyForConfiguredSdk(): Boolean = false

    override fun run() {
        init();
    }

    private fun init() {
        createSdkDir()
        createSdkRepoSettingsFile()
        downloadAndConfigureGradle()
        artifactManager.init()
        printWriter.println(messages["setup.sdkConfigured"].green())
    }

    private fun downloadAndConfigureGradle() {
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
        sdkSettings["sdk.metadata"] = sdkSettings.sdkHome().resolve("sdk.metadata").toString()
        sdkSettings["sdk.repositories"] = sdkSettings.sdkHome().resolve("sdk.repositories").toString()
        sdkSettings["gradle.home"] = sdkSettings.sdkHome().resolve("gradle").toString()
        sdkSettings["gradle.cache"] = sdkSettings.sdkHome().resolve(Path.of("gradle", "cache")).toString()
        sdkSettings.flushAppProperties()
    }

    private fun createSdkDir() {
        if (!Files.exists(sdkSettings.sdkHome())) {
            Files.createDirectories(sdkSettings.sdkHome())
        }
    }
}

