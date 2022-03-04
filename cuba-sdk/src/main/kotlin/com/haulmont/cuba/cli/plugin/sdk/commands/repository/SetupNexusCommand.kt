package com.haulmont.cuba.cli.plugin.sdk.commands.repository

import com.beust.jcommander.Parameters
import com.haulmont.cli.core.green
import com.haulmont.cli.core.prompting.Answer
import com.haulmont.cli.core.prompting.Answers
import com.haulmont.cli.core.prompting.Prompts
import com.haulmont.cli.core.prompting.QuestionsList
import com.haulmont.cli.core.red
import com.haulmont.cuba.cli.plugin.sdk.commands.AbstractSdkCommand
import com.haulmont.cuba.cli.plugin.sdk.di.sdkKodein
import com.haulmont.cuba.cli.plugin.sdk.dto.*
import com.haulmont.cuba.cli.plugin.sdk.nexus.NexusScriptManager
import com.haulmont.cuba.cli.plugin.sdk.services.MetadataHolder
import com.haulmont.cuba.cli.plugin.sdk.services.RepositoryManager
import com.haulmont.cuba.cli.plugin.sdk.utils.FileUtils
import com.haulmont.cuba.cli.plugin.sdk.utils.currentOsType
import com.haulmont.cuba.cli.plugin.sdk.utils.formatPath
import org.apache.commons.lang.BooleanUtils.isTrue
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

    internal val repositoryManager: RepositoryManager by sdkKodein.instance<RepositoryManager>()
    internal val nexusScriptManager: NexusScriptManager by sdkKodein.instance<NexusScriptManager>()
    internal val metadataHolder: MetadataHolder by sdkKodein.instance<MetadataHolder>()

    override fun run() {
        Prompts.create(kodein) { askRepositorySettings() }
            .let(Prompts::ask)
            .let(this::setupRepository)
    }

    private fun QuestionsList.askRepositorySettings() {
        question("repository-path", messages["setup.localRepositoryLocationCaption"]) {
            validate {
                if (value.contains("\\")) {
                    fail(messages["validation.notUseBackslashesInPaths"])
                }
            }
            default(sdkSettings.sdkHome().resolve("repository").toString().formatPath())
        }
        confirmation("upgrade-nexus", messages["setup.upgradeNexusCaption"].format(sdkSettings["nexus.version"])) {
            default(true)
            askIf { !repositoryPathIsEmpty(it) && nexusInstalled(it) }
        }
        confirmation("rewrite-install-path", messages["setup.localRepositoryRewriteInstallPathCaption"]) {
            default(true)
            askIf { !repositoryPathIsEmpty(it) && !upgradeRequired(it) }
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

    private fun upgradeRequired(it: Answers) =
        isTrue(it["upgrade-nexus"] as Boolean?)

    private fun nexusInstalled(answers: Answers): Boolean {
        val repositoryPath = answers["repository-path"]
        return repositoryPath != null && Files.exists(
            Path.of(
                repositoryPath as String
            ).resolve("nexus3")
        )
    }

    private fun nexusConfigurationRequired(it: Answers) =
        repositoryPathIsEmpty(it) || isTrue(it["rewrite-install-path"] as Boolean?)

    private fun repositoryPathIsEmpty(answers: Map<String, Answer>): Boolean {
        return answers["repository-path"] != null && !Files.exists(
            Path.of(
                answers["repository-path"] as String
            )
        )
    }

    private fun setupRepository(answers: Answers) {
        StopCommand().apply { checkState = false }.execute()
        if (repositoryPathIsEmpty(answers)
            || isTrue(answers["rewrite-install-path"] as Boolean?)
            || isTrue(answers["upgrade-nexus"] as Boolean?)
        ) {
            downloadAndConfigureNexus(answers)
        }
    }

    private fun downloadAndConfigureNexus(answers: Answers): Boolean {
        val installer =
            object : ToolInstaller("Nexus", nexusDownloadLink(), Path.of(answers["repository-path"] as String)) {
                override fun beforeUnzip(zipFilePath: Path): Path {
                    printWriter.println(messages["setup.unzipRepositoryCaption"].format(answers["repository-path"]))

                    if (isTrue(answers["rewrite-install-path"] as Boolean?)) {
                        if (Files.exists(installPath.resolve("sonatype-work"))) {
                            FileUtils.deleteDirectory(installPath.resolve("sonatype-work"))
                        }
                    }

                    return zipFilePath
                }

                override fun onUnzipFinished() {
                    installPath.resolve("nexus3").let { path ->
                        if (Files.exists(installPath.resolve("nexus3"))) {
                            FileUtils.deleteDirectory(installPath.resolve("nexus3"))
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
                makeNexusExecutable()
                StartCommand().execute()
                if (!isTrue(answers["upgrade-nexus"] as Boolean?)) {
                    configureNexus(answers)
                    addTargetSdkRepository(answers)
                    HashSet(metadataHolder.getInstalled()).forEach {
                        metadataHolder.removeInstalled(it)
                    }
                }
            },
            onFail = {
                printWriter.println(messages["setup.nexus.configurationFailed"].format(it.message).red())
                printWriter.println(messages["setup.nexus.configurationManual"].format(Path.of(answers["repository-path"] as String)))
                result = false
            }
        )
        return result
    }

    private fun makeNexusExecutable() {
        if (currentOsType() != OsType.WINDOWS) {
            val nexusExecutable: Path = Path.of(sdkSettings["repository.path"], "nexus3", "bin", "nexus")
            nexusExecutable.toFile().setExecutable(true)
        }
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
        val response = nexusScriptManager.create(login, password, "sdk.init", script)

        if (response.statusCode != 204) {
            printWriter.println(
                messages["setup.repositoryCanNotBeConfiguredAutomatically"].format(response.responseMessage).red()
            )
            return false
        }
        return true
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

        val port = when {
            answers["port"] != null -> {
                answers["port"]
            }
            properties["application-port"] != null -> {
                properties["application-port"]
            }
            else -> {
                "8085"
            }
        }

        properties["application-port"] = port
        properties["nexus.scripts.allowCreation"] = "true"
        properties["nexus.onboarding.enabled"] = "false"
        FileWriter(nexusConfig.toString()).use {
            properties.store(it, "Nexus properties")
        }
        sdkSettings["repository.type"] = "local"
        sdkSettings["repository.url"] = sdkSettings["template.repositoryUrl"].format(port)
        if (answers["repository-path"] != null) {
            sdkSettings["repository.path"] = answers["repository-path"] as String?
        }
        if (answers["repository-name"] != null) {
            sdkSettings["repository.name"] = answers["repository-name"] as String?
        }
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

