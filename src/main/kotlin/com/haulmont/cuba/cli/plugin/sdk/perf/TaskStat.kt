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

package com.haulmont.cuba.cli.plugin.sdk.perf

data class TaskStat(
    val taskName: String,
    var startTimeMillis: Long? = 0,
    var stopTimeMillis: Long? = 0,
    var avgTimeMillis: Double = 0.0,
    var totalTimeMillis: Long = 0,
    var minTimeMillis: Long? = 0,
    var maxTimeMillis: Long? = 0,
    var count: Int = 0
)