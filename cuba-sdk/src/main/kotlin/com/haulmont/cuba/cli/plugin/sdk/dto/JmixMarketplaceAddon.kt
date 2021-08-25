package com.haulmont.cuba.cli.plugin.sdk.dto

data class JmixMarketplaceAddon(
    val id: String,
    val name: String,
    val about: String,
    val description: String,
    val category: String,
    val tags: List<String>,
    val vendor: String,
    val updateDateTime: Long,
    val dependencies: List<Dependency>,
    var compatibilityList: List<MarketplaceAddonCompatibility>,
    val commercial: Boolean
)
