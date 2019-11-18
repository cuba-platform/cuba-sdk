package com.haulmont.cuba.cli.plugin.sdk.commands.repository

import com.beust.jcommander.Parameters
import com.haulmont.cuba.cli.Cli
import com.haulmont.cuba.cli.commands.AbstractCommand
import com.haulmont.cuba.cli.cubaplugin.di.sdkKodein
import com.haulmont.cuba.cli.localMessages
import com.haulmont.cuba.cli.plugin.sdk.SdkPlugin
import com.haulmont.cuba.cli.plugin.sdk.SdkPlugin.Companion.SDK_PATH
import com.haulmont.cuba.cli.plugin.sdk.commands.AbstractSdkCommand
import com.haulmont.cuba.cli.plugin.sdk.services.FileDownloadService
import com.haulmont.cuba.cli.plugin.sdk.services.SdkSettingsHolder
import com.haulmont.cuba.cli.prompting.Answer
import com.haulmont.cuba.cli.prompting.Answers
import com.haulmont.cuba.cli.prompting.Prompts
import com.haulmont.cuba.cli.prompting.QuestionsList
import org.kodein.di.generic.instance
import java.io.File
import java.io.PrintWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.*

@Parameters(commandDescription = "Setup SDK")
class SetupCommand : AbstractCommand() {

    internal val sdkSettings: SdkSettingsHolder by sdkKodein.instance()
    internal val fileDownloadService: FileDownloadService by sdkKodein.instance()
    private val printWriter: PrintWriter by kodein.instance()

    private val applicationProperties by lazy {
        val properties = Properties()

        val propertiesInputStream = SdkPlugin::class.java.getResourceAsStream("application.properties")
        propertiesInputStream.use {
            val inputStreamReader = java.io.InputStreamReader(propertiesInputStream, StandardCharsets.UTF_8)
            properties.load(inputStreamReader)
        }

        properties
    }

    val SDK_LOCAL_REPOSITORY_URL: String by lazy {
        applicationProperties["repositoryUrl"]!! as String
    }

    private val messages by localMessages()

    override fun run() {
        Prompts.create(kodein) { askRepositorySettings() }
            .let(Prompts::ask)
            .let(this::setupRepository)
    }


    private fun QuestionsList.askRepositorySettings() {
        question("repoType", messages["remoteOrLocalQuestionCaption"]) {
            validate {
                value.toLowerCase() == "remote" || value.toLowerCase() == "local"
            }
            default("local")
        }
        question("url", messages["remoteRepositoryURLCaption"]) {
            askIf { isRemoteRepository(it) }
        }
        question("install-path", messages["localRepositoryLocationCaption"]) {
            default(SDK_PATH.resolve("repository").toString())
            askIf { !isRemoteRepository(it) }
        }
        question("port", messages["localRepositoryPortCaption"]) {
            default("8081")
            askIf { !isRemoteRepository(it) }
        }
        question("login", messages["repositoryLoginCaption"]) {
            default("admin")
        }
        question("password", messages["repositoryPasswordCaption"]) {
            default("admin")
        }
    }

    private fun isRemoteRepository(it: Answers) = it["repoType"] == "remote"

    private fun setupRepository(answers: Answers) {
        createSdkDir()
        if (!isRemoteRepository(answers)) {
            downloadAndInstallNexusRepository(answers)
        }
        createSdkRepoSettingsFile(answers)
    }

    private fun createSdkRepoSettingsFile(answers: Answers) {
        for (answer in answers) {
            sdkSettings.setProperty(answer.key, resolveSdkProperty(answer, answers))
        }
        sdkSettings.flushAppProperties()
    }

    private fun resolveSdkProperty(answer: Map.Entry<String, Answer>, answers: Answers) = when (answer.key) {
        "url" -> {
            if (isRemoteRepository(answers)) answer.value.toString() else SDK_LOCAL_REPOSITORY_URL.format(answers["port"])
        }
        else -> answer.value.toString()
    }


    private fun createSdkDir() {
        if (!Files.exists(SDK_PATH)) {
            Files.createDirectories(SDK_PATH)
        }
    }

    private fun downloadAndInstallNexusRepository(answers: Answers) {
        fileDownloadService.downloadFile(
            applicationProperties["nexusDownloadLink_win64"] as String,
            File.createTempFile(SDK_PATH.toString(), "nexus.zip"),
            { bytesRead: Long, contentLength: Long, isDone: Boolean ->
                val progress = 100 * bytesRead.toFloat() / contentLength.toFloat()
                printWriter.print(progress as String + "% ")
            }
        )
    }


}

