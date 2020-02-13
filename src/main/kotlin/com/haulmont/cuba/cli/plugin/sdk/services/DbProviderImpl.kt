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

package com.haulmont.cuba.cli.plugin.sdk.services

import com.haulmont.cuba.cli.cubaplugin.di.sdkKodein
import com.haulmont.cuba.cli.plugin.sdk.db.DbInstance
import org.kodein.di.generic.instance
import org.mapdb.DBMaker.fileDB
import java.nio.file.Path

class DbProviderImpl : DbProvider {

    private val sdkSettings: SdkSettingsHolder by sdkKodein.instance()

    internal val dbInstances = mutableMapOf<String, DbInstance>()

    override fun get(storage: String): DbInstance {
        return dbInstances.getOrPut(storage, {
            DbInstance(
                fileDB(Path.of(sdkSettings["gradle.home"], "${storage}.db").toFile())
                    .fileMmapEnable()
                    .fileMmapEnableIfSupported()
                    .fileMmapPreclearDisable()
                    .fileChannelEnable()
                    .checksumHeaderBypass()
                    .closeOnJvmShutdown()
                    .executorEnable()
                    .make()
            )
        })
    }
}