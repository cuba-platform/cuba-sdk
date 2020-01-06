package com.haulmont.cuba.cli.plugin.sdk.commands.repository

import com.beust.jcommander.Parameters
import com.haulmont.cuba.cli.commands.AbstractCommand
import com.haulmont.cuba.cli.cubaplugin.di.sdkKodein
import com.haulmont.cuba.cli.localMessages
import com.haulmont.cuba.cli.plugin.sdk.SdkPlugin
import com.haulmont.cuba.cli.plugin.sdk.SdkPlugin.Companion.SDK_PATH
import com.haulmont.cuba.cli.plugin.sdk.dto.Authentication
import com.haulmont.cuba.cli.plugin.sdk.dto.Repository
import com.haulmont.cuba.cli.plugin.sdk.dto.RepositoryTarget
import com.haulmont.cuba.cli.plugin.sdk.dto.RepositoryType
import com.haulmont.cuba.cli.plugin.sdk.services.FileDownloadService
import com.haulmont.cuba.cli.plugin.sdk.services.RepositoryManager
import com.haulmont.cuba.cli.plugin.sdk.services.SdkSettingsHolder
import com.haulmont.cuba.cli.plugin.sdk.utils.FileUtils
import com.haulmont.cuba.cli.prompting.Answer
import com.haulmont.cuba.cli.prompting.Answers
import com.haulmont.cuba.cli.prompting.Prompts
import com.haulmont.cuba.cli.prompting.QuestionsList
import org.kodein.di.generic.instance
import java.io.PrintWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

@Parameters(commandDescription = "Setup SDK")
class SetupCommand : AbstractCommand() {

    internal val sdkSettings: SdkSettingsHolder by sdkKodein.instance()
    internal val fileDownloadService: FileDownloadService by sdkKodein.instance()
    internal val repositoryManager: RepositoryManager by sdkKodein.instance()
    private val printWriter: PrintWriter by kodein.instance()
    private val messages by localMessages()

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
        confirmation("rewrite-install-path", messages["localRepositoryRewriteInstallPathCaption"]) {
            default(false)
            askIf { !repositoryPathIsEmpty(it) }
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
        question("repository-name", messages["repositoryName"]) {
            default("cuba-sdk")
        }
    }

    private fun repositoryPathIsEmpty(answers: Map<String, Answer>): Boolean {
        return !Files.exists(Path.of((answers["install-path"] as String)))
    }

    private fun mavenPathIsEmpty(answers: Map<String, Answer>): Boolean {
        return !Files.exists(Path.of(sdkSettings.getProperty("mvn-install-path")))
    }

    private fun isRemoteRepository(it: Answers) = it["repoType"] == "remote"

    private fun setupRepository(answers: Answers) {
        createSdkDir()
        createSdkRepoSettingsFile(answers)
        downloadAndConfigureMaven(answers)
        if (needToInstallRepository(answers)) {
            downloadAndConfigureNexus(answers)
        }
        addTargetSdkRepository(answers)
    }

    private fun downloadAndConfigureMaven(answers: Answers) {
        downloadMaven(answers).also {
            if (mavenPathIsEmpty(answers)) {
                Files.createDirectory(Path.of((answers["mvn-install-path"] as String)))
                unzipMaven(answers, it)
            }
        }.also {
            configureMaven(answers, it)
        }
    }

    private fun downloadAndConfigureNexus(answers: Answers) {
        downloadRepository(answers).also {
            if (repositoryPathIsEmpty(answers)) {
                val installPath = answers["install-path"] as String
                Files.createDirectory(Path.of(installPath))
                unzipRepository(answers, it)
                Files.move(Path.of(installPath).resolve("nexus-3.19.1-01"), Path.of(installPath).resolve("nexus3"))
            }
        }.also {
            configureRepository(answers, it)
        }
    }

    private fun configureMaven(answers: Map<String, Answer>, it: Path) {
        repositoryManager.mvnSettingFile()
    }

    private fun unzipMaven(answers: Map<String, Answer>, it: Path) {
        printWriter.println(messages["unzipMavenCaption"])
        FileUtils.unzip(it, Path.of(sdkSettings.getProperty("mvn-install-path")), true)
    }

    private fun downloadMaven(answers: Map<String, Answer>): Path {
        val archive = SDK_PATH.resolve("maven.zip")
        if (!Files.exists(archive)) {
            printWriter.println(messages["downloadMaven"])
            val file = Files.createFile(archive)
            fileDownloadService.downloadFile(
                mavenDownloadLink(),
                file
            ) { bytesRead: Long, contentLength: Long, isDone: Boolean ->
                val progress = 100 * bytesRead.toFloat() / contentLength.toFloat()
                printWriter.print(messages["downloadProgress"].format(progress))
            }

        }
        printWriter.println()
        return archive
    }

    private fun mavenDownloadLink() = applicationProperties["mavenDownloadLink"] as String

    private fun addTargetSdkRepository(answers: Answers) {
        val repositoryName = answers["repository-name"] as String
        repositoryManager.getRepository(repositoryName, RepositoryTarget.TARGET)
            ?.let { repositoryManager.removeRepository(repositoryName, RepositoryTarget.TARGET) }
        repositoryManager.addRepository(
            Repository(
                name = repositoryName,
                type = RepositoryType.NEXUS2,
                url = if (!isRemoteRepository(answers)) {
                    SDK_LOCAL_REPOSITORY_URL.format(answers["port"])
                } else {
                    answers["url"] as String
                },
                authentication = Authentication(answers["login"] as String, answers["password"] as String)
            ), RepositoryTarget.TARGET
        )

    }

    private fun configureRepository(answers: Map<String, Answer>, path: Path) {
        StartCommand().execute()
    }

    private fun unzipRepository(answers: Answers, it: Path) {
        printWriter.println(messages["unzipRepositoryCaption"].format(answers["install-path"]))
        FileUtils.unzip(it, Path.of((answers["install-path"]) as String))
    }

    private fun needToInstallRepository(answers: Map<String, Answer>): Boolean {
        if (!isRemoteRepository(answers)) {
            if (repositoryPathIsEmpty(answers) || false != answers["rewrite-install-path"]) {
                return true;
            }
        }
        return false;
    }

    private fun createSdkRepoSettingsFile(answers: Answers) {
        for (answer in answers) {
            sdkSettings.setProperty(answer.key, answer.value.toString())
        }
        if (!isRemoteRepository(answers)) {
            sdkSettings.setProperty("url", SDK_LOCAL_REPOSITORY_URL.format(answers["port"]))
        }
        sdkSettings.setProperty("mvn-local-repo", SDK_PATH.resolve(".m2").toString())
        sdkSettings.setProperty("mvn-install-path", SDK_PATH.resolve("mvn").toString())
        sdkSettings.flushAppProperties()
    }

    private fun createSdkDir() {
        if (!Files.exists(SDK_PATH)) {
            Files.createDirectories(SDK_PATH)
        }
    }

    private fun downloadRepository(answers: Answers): Path {
        val repositoryArchive = SDK_PATH.resolve("nexus.zip")
        if (!Files.exists(repositoryArchive)) {
            printWriter.println(messages["downloadNexus"])
            val file = Files.createFile(repositoryArchive)
            fileDownloadService.downloadFile(
                nexusDownloadLink(),
                file
            ) { bytesRead: Long, contentLength: Long, isDone: Boolean ->
                val progress = 100 * bytesRead.toFloat() / contentLength.toFloat()
                printWriter.print(messages["downloadProgress"].format(progress))
            }
        }
        printWriter.println()
        return repositoryArchive
    }

    private fun nexusDownloadLink() = applicationProperties["nexusDownloadLink_win64"] as String


}

