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

import com.haulmont.cuba.cli.plugin.sdk.db.DbInstance
import com.haulmont.cuba.cli.plugin.sdk.di.sdkKodein
import org.kodein.di.generic.instance
import org.mapdb.DBMaker.fileDB
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.concurrent.locks.ReentrantLock

class DbProviderImpl : DbProvider {

    private val sdkSettings: SdkSettingsHolder by sdkKodein.instance<SdkSettingsHolder>()

    internal val dbInstances = Collections.synchronizedMap(mutableMapOf<String, DbInstance>())

    internal val lock = ReentrantLock()

    override fun get(storage: String): DbInstance {
        if (dbInstances.containsKey(storage)) {
            return dbInstances[storage]!!
        } else {
            synchronized(lock) {
                return dbInstances.getOrPut(storage, {
                    DbInstance(
                        fileDB(sdkSettings.sdkHome().resolve(Path.of("${storage}.db")).toFile())
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
    }

    override fun dbExists(storage: String) = Files.exists(sdkSettings.sdkHome().resolve(Path.of("${storage}.db")))
}