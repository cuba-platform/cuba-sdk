package com.haulmont.cuba.cli.plugin.sdk.templates.provider.nexus

import com.github.kittinunf.fuel.httpGet
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.haulmont.cli.core.prompting.Option
import com.haulmont.cuba.cli.plugin.sdk.templates.provider.BaseComponentProvider
import com.haulmont.cuba.cli.plugin.sdk.utils.splitVersion


// This provider is used for interaction with one of the Jmix repos

abstract class Nexus3SearchComponentProvider(framework: String) : BaseComponentProvider(framework) {

    override fun loadVersions(componentId: String): List<Option<String>> {
        val split = componentId.split(":")
        if (split.size >= 2) {
            val groupId = split[0]
            val artifactId = split[1]
            val result =
                sdkSettings.get("${framework}.artifact.base.url").plus("group=${groupId}&name=${artifactId}")
                    .httpGet()
                    .responseString()
                    .third

            result.fold(
                success = {
                    val json: JsonObject = Gson().fromJson(it, JsonObject::class.java)
                    return json.getAsJsonArray("items")
                        .map { it.asJsonObject.get("version") }
                        .map{ it.asString }
                        .asSequence()
                        .distinct()
                        .map { it.splitVersion() }
                        .filterNotNull()
                        .groupBy { it.major }
                        .entries
                        .asSequence()
                        .map { it.value.filter { it.minor!=null }.maxByOrNull { v -> v.minor!! } }
                        .filterNotNull()
                        .sortedByDescending { it.major }
                        .map { it.version }
                        .map { Option(it, it, it) }
                        .toList()
                },
                failure = { error ->
                    return emptyList()
                }
            )
        }
        return emptyList()
    }
}