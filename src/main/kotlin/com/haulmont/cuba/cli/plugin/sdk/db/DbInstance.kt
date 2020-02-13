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

package com.haulmont.cuba.cli.plugin.sdk.db

import org.mapdb.DB
import org.mapdb.Serializer
import java.util.concurrent.locks.ReentrantLock

class DbInstance(val db: DB) {

    internal val lock = ReentrantLock()

    operator fun set(key: String, value: String) {
        db.hashMap(
            "map", Serializer.STRING, Serializer.STRING
        ).createOrOpen().set(key, value)
    }

    fun put(map: Map<String, String>) {
        synchronized(lock) {
            db.use {
                it.hashMap(
                    "map", Serializer.STRING, Serializer.STRING
                ).createOrOpen().putAll(map)
            }
        }
    }

    operator fun get(key: String): String? {
        val map = db.hashMap(
            "map", Serializer.STRING, Serializer.STRING
        ).createOrOpen()
        return map[key]
    }

}