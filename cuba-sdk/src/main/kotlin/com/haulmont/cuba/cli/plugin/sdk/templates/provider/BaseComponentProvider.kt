package com.haulmont.cuba.cli.plugin.sdk.templates.provider

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import com.haulmont.cli.core.prompting.Option
import com.haulmont.cuba.cli.plugin.sdk.di.sdkKodein
import com.haulmont.cuba.cli.plugin.sdk.dto.Version
import com.haulmont.cuba.cli.plugin.sdk.services.SdkSettingsHolder
import com.haulmont.cuba.cli.plugin.sdk.templates.ComponentProvider
import org.kodein.di.generic.instance
import java.util.concurrent.TimeUnit
import java.util.regex.Matcher
import java.util.regex.Pattern

abstract class BaseComponentProvider(protected val framework : String) : ComponentProvider {

    protected val sdkSettings: SdkSettingsHolder by sdkKodein.instance<SdkSettingsHolder>()

    var versionsCache: LoadingCache<String, List<Option<String>>> = CacheBuilder.newBuilder()
        .expireAfterWrite(10, TimeUnit.MINUTES)
        .build(
            object : CacheLoader<String, List<Option<String>>>() {
                override fun load(componentId: String): List<Option<String>> {
                    return loadVersions(componentId)
                }
            })


    override fun versions(componentId: String?): List<Option<String>> {
        componentId?.let {
            return versionsCache.getUnchecked(componentId)
        }
        return emptyList()
    }

    protected abstract fun loadVersions(componentId: String) : List<Option<String>>

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