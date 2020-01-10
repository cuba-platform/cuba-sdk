package com.haulmont.cuba.cli.plugin.sdk.commands.repository

import com.beust.jcommander.Parameters
import com.github.kittinunf.fuel.core.Headers
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.httpDelete
import com.github.kittinunf.fuel.httpPost
import com.haulmont.cuba.cli.commands.AbstractCommand
import com.haulmont.cuba.cli.cubaplugin.di.sdkKodein
import com.haulmont.cuba.cli.green
import com.haulmont.cuba.cli.localMessages
import com.haulmont.cuba.cli.plugin.sdk.SdkPlugin
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
import com.haulmont.cuba.cli.red
import org.json.JSONObject
import org.kodein.di.generic.instance
import java.io.FileInputStream
import java.io.FileWriter
import java.io.InputStreamReader
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

    val SDK_LOCAL_REPOSITORY_URL: String by lazy {
        sdkSettings["template.repositoryUrl"]
    }


    override fun run() {
        Prompts.create(kodein) { askRepositorySettings() }
            .let(Prompts::ask)
            .let(this::setupRepository)
    }


    private fun QuestionsList.askRepositorySettings() {
        question("repository.type", messages["remoteOrLocalQuestionCaption"]) {
            validate {
                value.toLowerCase() == "remote" || value.toLowerCase() == "local"
            }
            default("local")
        }
        question("url", messages["remoteRepositoryURLCaption"]) {
            askIf { isRemoteRepository(it) }
        }
        question("repository.path", messages["localRepositoryLocationCaption"]) {
            default(sdkSettings.sdkHome.resolve("repository").toString())
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
        return !Files.exists(
            Path.of(
                answers["repository.path"] as String
            )
        )
    }

    private fun mavenPathIsEmpty(answers: Map<String, Answer>): Boolean {
        return !Files.exists(
            sdkSettings.sdkHome.resolve(sdkSettings["mvn.path"])
        )
    }

    private fun isRemoteRepository(it: Answers) = it["repository.type"] == "remote"

    private fun setupRepository(answers: Answers) {
        createSdkDir()
        createSdkRepoSettingsFile(answers)
        downloadAndConfigureMaven(answers)
        if (needToInstallRepository(answers)) {
            downloadAndConfigureNexus(answers)
        }
        addTargetSdkRepository(answers)
        printWriter.println(messages["setup.sdkConfigured"].green())
    }

    private fun downloadAndConfigureMaven(answers: Answers) {
        downloadMaven(answers).also {
            if (mavenPathIsEmpty(answers)) {
                Files.createDirectory(
                    sdkSettings.sdkHome.resolve(sdkSettings["mvn.path"])
                )
                unzipMaven(
                    answers, it
                )
            }
        }.also {
            configureMaven(answers, it)
        }
    }

    private fun downloadAndConfigureNexus(answers: Answers) {
        downloadRepository(answers).also {
            if (repositoryPathIsEmpty(answers)) {
                val installPath = answers["repository.path"] as String
                Files.createDirectory(Path.of(installPath))
                unzipRepository(answers, it)
                Files.move(
                    Path.of(installPath).resolve("nexus-" + sdkSettings["nexus.version"]),
                    Path.of(installPath).resolve("nexus3")
                )
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
        FileUtils.unzip(
            it,
            sdkSettings.sdkHome.resolve(sdkSettings["mvn.path"]),
            true
        )
    }

    private fun downloadMaven(answers: Map<String, Answer>): Path {
        val archive = sdkSettings.sdkHome.resolve("maven.zip")
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

    private fun mavenDownloadLink() = sdkSettings["maven.downloadLink"]
        .format(
            sdkSettings["maven.version"],
            sdkSettings["maven.version"]
        )

    private fun addTargetSdkRepository(answers: Answers) {
        val repositoryName = answers["repository-name"] as String
        addRepository(repositoryName, RepositoryType.NEXUS3, answers)
    }

    private fun addRepository(repositoryName: String, repositoryType: RepositoryType, answers: Answers) {
        repositoryManager.getRepository(repositoryName, RepositoryTarget.TARGET)
            ?.let { repositoryManager.removeRepository(repositoryName, RepositoryTarget.TARGET) }
        repositoryManager.addRepository(
            repositoryFromAnswers(repositoryName, repositoryType, answers), RepositoryTarget.TARGET
        )
    }

    private fun repositoryFromAnswers(
        repositoryName: String,
        repositoryType: RepositoryType,
        answers: Answers
    ): Repository {
        return Repository(
            name = repositoryName,
            type = repositoryType,
            url = if (!isRemoteRepository(answers)) {
                sdkSettings["repository.url"] + "repository/${repositoryName}/"
            } else {
                answers["url"] as String
            },
            authentication = if (answers["login"] != null) {
                Authentication(answers["login"] as String, answers["password"] as String)
            } else {
                null
            },
            repositoryName = repositoryName
        )
    }

    private fun configureRepository(answers: Map<String, Answer>, path: Path) {
        configureNexusProperties(answers)
        StopCommand().execute()
        StartCommand().execute()
        configureNexus(answers)
        printWriter.println(messages["setup.nexusConfigured"].green())
    }

    private fun configureNexus(answers: Answers) {
        printWriter.println(messages["setup.applyRepositoryCredentials"])
        val adminPassword =
            Path.of(answers["repository.path"] as String, "sonatype-work", "nexus3", "admin.password")
        if (Files.exists(adminPassword)) {
            runNexusConfigurationScript(answers, "admin", adminPassword.toFile().readText(StandardCharsets.UTF_8))
        } else {
            runNexusConfigurationScript(answers, sdkSettings["repository.login"], sdkSettings["repository.password"])
        }
        persistSdkCredentials(answers)
        if (Files.exists(adminPassword)) {
            Files.delete(adminPassword)
        }
    }

    private fun runNexusConfigurationScript(answers: Answers, login: String, password: String) {
        val script = SdkPlugin::class.java
            .getResourceAsStream("scripts/createRepository.groovy")
            .bufferedReader()
            .use { it.readText() }
        if (createNexusScript(login, password, script)) {
            runNexusScript(answers, login, password)
            dropNexusScript(answers)
        }
    }

    private fun dropNexusScript(answers: Map<String, Answer>) {
        "${sdkSettings["repository.url"]}service/rest/v1/script/sdk-init"
            .httpDelete()
            .authentication()
            .basic(answers["login"] as String, answers["password"] as String)
            .header(Headers.ACCEPT, "application/json")
            .response()
    }

    private fun createNexusScript(login: String, password: String, script: String): Boolean {
        val jsonObject = JSONObject()
            .put("name", "sdk-init")
            .put("type", "groovy")
            .put("content", script)
        val payload = jsonObject.toString()
        val (_, response, _) =
            "${sdkSettings["repository.url"]}service/rest/v1/script"
                .httpPost()
                .authentication()
                .basic(login, password)
                .header(Headers.CONTENT_TYPE, "application/json")
                .header(Headers.ACCEPT, "application/json")
                .header(Headers.CACHE_CONTROL, "no-cache")
                .body(jsonObject.toString())
                .response()
        if (response.statusCode != 204) {
            printWriter.println(messages["setup.repositoryCanNotBeConfiguredAutomatically"].format(response.responseMessage).red())
            return false
        }
        return true
    }

    private fun runNexusScript(answers: Answers, login: String, password: String): Boolean {
        val (_, response, _) =
            "${sdkSettings["repository.url"]}service/rest/v1/script/sdk-init/run"
                .httpPost()
                .authentication()
                .basic(login, password)
                .header(Headers.CONTENT_TYPE, "application/json")
                .header(Headers.ACCEPT, "application/json")
                .header(Headers.CACHE_CONTROL, "no-cache")
                .body(
                    """
                    {
                        "login": "${answers["login"]}",
                        "password": "${answers["password"]}",
                        "repoName": "${answers["repository-name"]}"
                    }
                    """
                )
                .response()
        if (response.statusCode != 200) {
            printWriter.println(messages["setup.repositoryCanNotBeConfiguredAutomatically"].format(response.responseMessage).red())
            return false
        }
        return true
    }

    private fun persistSdkCredentials(answers: Answers) {
        sdkSettings["repository.login"] = answers["login"] as String
        sdkSettings["repository.password"] = answers["password"] as String
        sdkSettings.flushAppProperties()
    }

    private fun configureNexusProperties(answers: Map<String, Answer>) {
        printWriter.println(messages["setup.configureNexus"])
        val nexusConfig =
            Path.of(answers["repository.path"] as String, "sonatype-work", "nexus3", "etc", "nexus.properties")
                .also {
                    if (!Files.exists(it)) {
                        Files.createDirectories(it.parent)
                        Files.createFile(it)
                    }
                }
        val properties = Properties()

        val propertiesInputStream = FileInputStream(nexusConfig.toString())
        propertiesInputStream.use {
            val inputStreamReader = InputStreamReader(propertiesInputStream, StandardCharsets.UTF_8)
            properties.load(inputStreamReader)
        }

        properties["application-port"] = answers["port"]
        FileWriter(nexusConfig.toString()).use {
            properties.store(it, "Nexus properties")
        }
    }

    private fun unzipRepository(answers: Answers, it: Path) {
        printWriter.println(messages["unzipRepositoryCaption"].format(answers["repository.path"]))
        FileUtils.unzip(it, Path.of((answers["repository.path"]) as String))
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
        if (!isRemoteRepository(answers)) {
            sdkSettings["repository.url"] = SDK_LOCAL_REPOSITORY_URL.format(answers["port"])
        }
        sdkSettings["repository.type"] = answers["repository.type"] as String
        sdkSettings["repository.path"] = answers["repository.path"] as String
        sdkSettings["sdk.home"] = sdkSettings.sdkHome.toString()
        sdkSettings["mvn.local.repo"] = sdkSettings.sdkHome.resolve(".m2").toString()
        sdkSettings["mvn.path"] = sdkSettings.sdkHome.resolve("mvn").toString()
        sdkSettings.flushAppProperties()
    }

    private fun createSdkDir() {
        if (!Files.exists(sdkSettings.sdkHome)) {
            Files.createDirectories(sdkSettings.sdkHome)
        }
    }

    private fun downloadRepository(answers: Answers): Path {
        val repositoryArchive = sdkSettings.sdkHome.resolve("nexus.zip")
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

    private fun nexusDownloadLink(): String {
        val dowloadLink = sdkSettings["nexus.downloadLink.win64"]
        val nexusVersion = sdkSettings["nexus.version"]
        return dowloadLink.format(nexusVersion)
    }


}

