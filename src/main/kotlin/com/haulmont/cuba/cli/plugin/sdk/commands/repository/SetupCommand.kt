package com.haulmont.cuba.cli.plugin.sdk.commands.repository

import com.beust.jcommander.Parameters
import com.haulmont.cuba.cli.commands.AbstractCommand
import com.haulmont.cuba.cli.cubaplugin.di.sdkKodein
import com.haulmont.cuba.cli.localMessages
import com.haulmont.cuba.cli.plugin.sdk.SdkPlugin
import com.haulmont.cuba.cli.plugin.sdk.SdkPlugin.Companion.SDK_PATH
import com.haulmont.cuba.cli.plugin.sdk.services.FileDownloadService
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
        question("maven-install-path", messages["mavenInstallPathCaption"]) {
            default(SDK_PATH.resolve("mvn").toString())
        }
        confirmation("rewrite-maven-install-path", messages["mavenRewriteInstallPathCaption"]) {
            default(false)
            askIf { !mavenPathIsEmpty(it) }
        }
    }

    private fun repositoryPathIsEmpty(answers: Map<String, Answer>): Boolean {
        return !Files.exists(Path.of((answers["install-path"] as String)))
    }

    private fun mavenPathIsEmpty(answers: Map<String, Answer>): Boolean {
        return !Files.exists(Path.of((answers["maven-install-path"] as String)))
    }

    private fun isRemoteRepository(it: Answers) = it["repoType"] == "remote"

    private fun setupRepository(answers: Answers) {
        createSdkDir()
        createSdkRepoSettingsFile(answers)

        if (needToInstallRepository(answers)) {
            downloadRepository(answers).also {
                if (repositoryPathIsEmpty(answers)) {
                    Files.createDirectory(Path.of((answers["install-path"] as String)))
                }
                unzipRepository(answers, it)
            }.also {
                configureRepository(answers, it)
            }
        }
    }

    private fun configureRepository(answers: Map<String, Answer>, path: Path): Any {
        return path
    }

    private fun unzipRepository(answers: Answers, it: Path) {
        printWriter.println(messages["unzipRepositoryCaption"].format(answers["install-path"]))
        FileUtils.unzip(it, Path.of((answers["install-path"]) as String))
    }

    private fun needToInstallRepository(answers: Map<String, Answer>): Boolean {
        if (!isRemoteRepository(answers)) {
            if (repositoryPathIsEmpty(answers) || answers["rewrite-install-path"] as Boolean) {
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
            val file = Files.createFile(repositoryArchive)
            fileDownloadService.downloadFile(
                applicationProperties["nexusDownloadLink_win64"] as String,
                file
            ) { bytesRead: Long, contentLength: Long, isDone: Boolean ->
                val progress = 100 * bytesRead.toFloat() / contentLength.toFloat()
                printWriter.print(messages["nexusDownloadProgress"].format(progress))
                if (isDone) {
                    printWriter.println()
                }
            }

        }
        return repositoryArchive
    }


}

