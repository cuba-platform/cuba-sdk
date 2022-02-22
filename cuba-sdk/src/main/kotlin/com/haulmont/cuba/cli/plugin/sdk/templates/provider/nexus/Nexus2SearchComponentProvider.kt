package com.haulmont.cuba.cli.plugin.sdk.templates.provider.nexus

import com.github.kittinunf.fuel.httpGet
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.haulmont.cli.core.prompting.Option
import com.haulmont.cuba.cli.plugin.sdk.templates.provider.BaseComponentProvider
import com.haulmont.cuba.cli.plugin.sdk.utils.splitVersion

// // This provider is used for interaction with the main CUBA repo

abstract class Nexus2SearchComponentProvider() : BaseComponentProvider() {

    override fun loadVersions(componentId: String): List<Option<String>> {
        val split = componentId.split(":")
        if (split.size >= 2) {
            val groupId = split[0]
            val artifactId = split[1]
            val result =
                sdkSettings.get("cuba.artifact.base.url").plus("g=${groupId}&a=${artifactId}")
                    .httpGet()
                    .header(Pair("Accept", "application/json"))
                    .responseString()
                    .third

            result.fold(
                success = {
                    val json: JsonObject = Gson().fromJson(it, JsonObject::class.java)
                    return json.getAsJsonArray("data")
                        .map { it.asJsonObject.get("version") }
                        .map{ it.asString }
                        .asSequence()
                        .distinct()
                        .map { it.splitVersion() }
                        .filterNotNull()
                        .groupBy { it.major }
                        .entries
                        .asSequence()
                        .map { it.value.filter { it.minor!=null }.maxBy { v -> v.minor!! } }
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