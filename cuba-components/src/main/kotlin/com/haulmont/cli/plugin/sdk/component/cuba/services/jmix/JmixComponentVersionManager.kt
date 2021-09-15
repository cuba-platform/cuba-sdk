package com.haulmont.cli.plugin.sdk.component.cuba.services.jmix

import com.haulmont.cuba.cli.plugin.sdk.dto.JmixMarketplaceAddon

interface JmixComponentVersionManager {

    fun addons(): List<JmixMarketplaceAddon>

    fun load(loadCompletedFun: (addons: List<JmixMarketplaceAddon>) -> Unit)
}