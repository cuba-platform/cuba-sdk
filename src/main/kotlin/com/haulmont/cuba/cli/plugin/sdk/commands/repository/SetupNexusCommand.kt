package com.haulmont.cuba.cli.plugin.sdk.commands.repository

import com.beust.jcommander.Parameters
import com.haulmont.cuba.cli.cubaplugin.di.sdkKodein
import com.haulmont.cuba.cli.green
import com.haulmont.cuba.cli.plugin.sdk.commands.AbstractSdkCommand
import com.haulmont.cuba.cli.plugin.sdk.dto.*
import com.haulmont.cuba.cli.plugin.sdk.nexus.NexusScriptManager
import com.haulmont.cuba.cli.plugin.sdk.services.RepositoryManager
import com.haulmont.cuba.cli.plugin.sdk.utils.FileUtils
import com.haulmont.cuba.cli.plugin.sdk.utils.currentOsType
import com.haulmont.cuba.cli.prompting.Answer
import com.haulmont.cuba.cli.prompting.Answers
import com.haulmont.cuba.cli.prompting.Prompts
import com.haulmont.cuba.cli.prompting.QuestionsList
import com.haulmont.cuba.cli.red
import org.gradle.internal.impldep.org.apache.commons.lang.BooleanUtils
import org.json.JSONObject
import org.kodein.di.generic.instance
import java.io.FileInputStream
import java.io.FileWriter
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.*


@Parameters(commandDescription = "Setup embedded nexus")
class SetupNexusCommand : AbstractSdkCommand() {

    internal val repositoryManager: RepositoryManager by sdkKodein.instance()
    internal val nexusScriptManager: NexusScriptManager by sdkKodein.instance()

    override fun run() {
        Prompts.create(kodein) { askRepositorySettings() }
            .let(Prompts::ask)
            .let(this::setupRepository)
    }

    private fun QuestionsList.askRepositorySettings() {
        question("repository-path", messages["setup.localRepositoryLocationCaption"]) {
            default(sdkSettings.sdkHome().resolve("repository").toString())
        }
        confirmation("rewrite-install-path", messages["setup.localRepositoryRewriteInstallPathCaption"]) {
            default(true)
            askIf { !repositoryPathIsEmpty(it) }
        }
        question("port", messages["setup.localRepositoryPortCaption"]) {
            default("8085")
            askIf { nexusConfigurationRequired(it) }
        }
        question("login", messages["setup.repositoryLoginCaption"]) {
            default("admin")
            askIf { nexusConfigurationRequired(it) }
        }
        question("password", messages["setup.repositoryPasswordCaption"]) {
            default("admin")
            askIf { nexusConfigurationRequired(it) }
        }
        question("repository-name", messages["setup.repositoryName"]) {
            default("cuba-sdk")
            askIf { nexusConfigurationRequired(it) }
        }
    }

    private fun nexusConfigurationRequired(it: Answers) =
        repositoryPathIsEmpty(it) || BooleanUtils.isTrue(it["rewrite-install-path"] as Boolean?)

    private fun repositoryPathIsEmpty(answers: Map<String, Answer>): Boolean {
        return answers["repository-path"] != null && !Files.exists(
            Path.of(
                answers["repository-path"] as String
            )
        )
    }

    private fun setupRepository(answers: Answers) {
        StopCommand().apply { checkState = false }.execute()
        if (repositoryPathIsEmpty(answers) || answers["rewrite-install-path"] as Boolean) {
            if (downloadAndConfigureNexus(answers)) {
                addTargetSdkRepository(answers)
            }
        }
    }

    private fun downloadAndConfigureNexus(answers: Answers): Boolean {
        val installer =
            object : ToolInstaller("Nexus", nexusDownloadLink(), Path.of(answers["repository-path"] as String)) {
                override fun beforeUnzip() {
                    Companion.printWriter.println(messages["setup.unzipRepositoryCaption"].format(answers["repository-path"]))
                }

                override fun onUnzipFinished() {
                    installPath.resolve("nexus3").let { path ->
                        if (Files.exists(path)) {
                            FileUtils.deleteDirectory(path)
                        }
                    }

                    Files.move(
                        installPath.resolve("nexus-" + sdkSettings["nexus.version"]),
                        installPath.resolve("nexus3"),
                        StandardCopyOption.REPLACE_EXISTING
                    )
                }
            }

        var result = true

        installer.downloadAndConfigure(
            configure = {
                configureNexusProperties(answers)
                StartCommand().execute()
                configureNexus(answers)
            },
            onFail = {
                printWriter.println(messages["setup.nexus.configurationFailed"].format(it.message).red())
                printWriter.println(messages["setup.nexus.configurationManual"].format(Path.of(answers["repository-path"] as String)))
                result = false;
            }
        )
        return result
    }

    private fun addTargetSdkRepository(answers: Answers) {
        val repositoryName = answers["repository-name"] as String
        addRepository(repositoryName, RepositoryType.NEXUS3, answers)
    }

    private fun addRepository(repositoryName: String, repositoryType: RepositoryType, answers: Answers) {
        repositoryManager.getRepository(repositoryName, RepositoryTarget.TARGET)
            ?.let { repositoryManager.removeRepository(repositoryName, RepositoryTarget.TARGET, true) }
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
            url = sdkSettings["repository.url"] + "repository/${repositoryName}/",
            authentication = if (answers["login"] != null) {
                Authentication(answers["login"] as String, answers["password"] as String)
            } else {
                null
            },
            repositoryName = repositoryName
        )
    }

    private fun configureNexus(answers: Answers) {

        printWriter.println(messages["setup.applyRepositoryCredentials"])
        val adminPassword =
            Path.of(answers["repository-path"] as String, "sonatype-work", "nexus3", "admin.password")
        if (Files.exists(adminPassword)) {
            if (runNexusConfigurationScript(
                    answers,
                    "admin",
                    adminPassword.toFile().readText(StandardCharsets.UTF_8)
                )
            ) {
                Files.delete(adminPassword)
                persistSdkCredentials(answers)
                printWriter.println(messages["setup.nexusConfigured"].green())
            }
        } else {
            var login = answers["login"] as String
            var password = answers["password"] as String
            repositoryManager.getRepository(sdkSettings["repository.name"], RepositoryTarget.TARGET)?.let {
                val authentication = it.authentication
                if (authentication != null) {
                    login = authentication.login
                    password = authentication.password
                }
            }
            if (runNexusConfigurationScript(answers, login, password)) {
                persistSdkCredentials(answers)
                printWriter.println(messages["setup.nexusConfigured"].green())
            }
        }
    }

    private fun runNexusConfigurationScript(answers: Answers, login: String, password: String): Boolean {
        if (createNexusScript(login, password, nexusScriptManager.loadScript("createRepository.groovy"))) {
            if (!runNexusScript(answers, login, password)) {
                return false
            }
            dropNexusScript(answers)
            addAdditionalScripts(answers)
            return true
        }
        return false
    }

    private fun addAdditionalScripts(answers: Map<String, Answer>) {
        val login = answers["login"] as String
        val password = answers["password"] as String
        nexusScriptManager.drop(login, password, "sdk.cleanup")
        nexusScriptManager.create(
            login,
            password,
            "sdk.cleanup",
            nexusScriptManager.loadScript("cleanupRepository.groovy")
        ).also {
            if (it.statusCode != 204) {
                printWriter.println(
                    messages["setup.repositoryCanNotBeConfiguredAutomatically"].format(it.responseMessage).red()
                )
            }
        }
        nexusScriptManager.drop(login, password, "sdk.drop-component")
        nexusScriptManager.create(
            login,
            password,
            "sdk.drop-component",
            nexusScriptManager.loadScript("dropComponent.groovy")
        ).also {
            if (it.statusCode != 204) {
                printWriter.println(
                    messages["setup.repositoryCanNotBeConfiguredAutomatically"].format(it.responseMessage).red()
                )
            }
        }
    }

    private fun dropNexusScript(answers: Map<String, Answer>) {
        nexusScriptManager.drop(answers["login"] as String, answers["password"] as String, "sdk.init")
    }

    private fun createNexusScript(login: String, password: String, script: String): Boolean {
        nexusScriptManager.create(login, password, "sdk.init", script)
            .also {
                if (it.statusCode != 204) {
                    printWriter.println(
                        messages["setup.repositoryCanNotBeConfiguredAutomatically"].format(it.responseMessage).red()
                    )
                    return false
                }
                return true
            }
        return false
    }

    private fun runNexusScript(answers: Answers, login: String, password: String): Boolean {
        nexusScriptManager.run(
            login, password, "sdk.init", JSONObject()
                .put("login", "${answers["login"]}")
                .put("password", "${answers["password"]}")
                .put("repoName", "${answers["repository-name"]}")
        ).also {
            if (it.statusCode != 200) {
                printWriter.println(
                    messages["setup.repositoryCanNotBeConfiguredAutomatically"].format(it.responseMessage).red()
                )
                return false
            }
            return true
        }
        return false
    }

    private fun persistSdkCredentials(answers: Answers) {
        sdkSettings["repository.login"] = answers["login"] as String
        sdkSettings["repository.name"] = answers["repository-name"] as String
        sdkSettings.flushAppProperties()
    }

    private fun configureNexusProperties(answers: Map<String, Answer>) {
        printWriter.println(messages["setup.configureNexus"])
        val nexusConfig =
            Path.of(answers["repository-path"] as String, "sonatype-work", "nexus3", "etc", "nexus.properties")
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
        properties["nexus.scripts.allowCreation"] = "true"
        FileWriter(nexusConfig.toString()).use {
            properties.store(it, "Nexus properties")
        }
        sdkSettings["repository.type"] = "local"
        sdkSettings["repository.url"] = sdkSettings["template.repositoryUrl"].format(answers["port"])
        sdkSettings["repository.path"] = answers["repository-path"] as String?
        sdkSettings["repository.name"] = answers["repository-name"] as String?
        sdkSettings.flushAppProperties()
    }

    private fun nexusDownloadLink(): String {
        val downloadLink = when (currentOsType()) {
            OsType.WINDOWS -> sdkSettings["nexus.downloadLink.win64"]
            OsType.LINUX -> sdkSettings["nexus.downloadLink.unix"]
            OsType.MAC -> sdkSettings["nexus.downloadLink.mac"]
        }
        val nexusVersion = sdkSettings["nexus.version"]
        return downloadLink.replace("<version>", nexusVersion)
    }
}

