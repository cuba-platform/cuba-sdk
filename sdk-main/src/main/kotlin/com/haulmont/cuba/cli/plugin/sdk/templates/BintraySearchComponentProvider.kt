/*
 * Copyright (c) 2008-2020 Haulmont.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.haulmont.cuba.cli.plugin.sdk.templates

import com.github.kittinunf.fuel.httpGet
import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.haulmont.cli.core.prompting.Option
import com.haulmont.cuba.cli.plugin.sdk.dto.Version
import com.haulmont.cuba.cli.plugin.sdk.utils.splitVersion
import java.util.concurrent.TimeUnit
import java.util.regex.Matcher
import java.util.regex.Pattern


abstract class BintraySearchComponentProvider : ComponentProvider {

    var versionsCache: LoadingCache<String, List<Option<String>>> = CacheBuilder.newBuilder()
        .expireAfterWrite(10, TimeUnit.MINUTES)
        .build(
            object : CacheLoader<String, List<Option<String>>>() {
                override fun load(componentId: String): List<Option<String>> {
                    return loadVersions(componentId)
                }
            })

    override fun availableVersions(componentId: String?): List<Option<String>> {
        componentId?.let {
            return versionsCache.getUnchecked(componentId)
        }
        return emptyList()
    }

    private fun loadVersions(componentId: String): List<Option<String>> {
        val split = componentId.split(":")
        if (split.size >= 2) {
            val groupId = split[0]
            val artifactId = split[1]
            val result =
                "https://api.bintray.com/search/packages/maven?g=${groupId}&a=${artifactId}"
                    .httpGet()
                    .responseString()
                    .third

            result.fold(
                success = {
                    val json: JsonArray = Gson().fromJson(it, JsonArray::class.java)
                    return json.map { it as JsonObject }
                        .flatMap { it.getAsJsonArray("versions").map { it.asString } }
                        .asSequence()
                        .distinct()
                        .map { it.splitVersion() }
                        .filterNotNull()
                        .groupBy { it.major }
                        .entries
                        .asSequence()
                        .map { it.value.filter { it.minor!=null }.maxBy { v-> v.minor!! } }
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

    internal fun splitVersion(version: String): Version? {
        val versionPattern: Pattern = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)(?:[.-]([a-zA-Z0-9]+))?")
        val matcher: Matcher = versionPattern.matcher(version)

        if (matcher.matches()) {
            val majorVersion = matcher.group(1) + "." + matcher.group(2)
            val qualifier = matcher.group(4)
            val minorVersion = Integer.parseInt(matcher.group(3))
            return Version(version, majorVersion, minorVersion, qualifier)
        }
        return null
    }
}