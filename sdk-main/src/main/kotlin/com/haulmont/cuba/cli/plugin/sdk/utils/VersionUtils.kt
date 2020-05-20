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

package com.haulmont.cuba.cli.plugin.sdk.utils

import com.haulmont.cuba.cli.plugin.sdk.dto.Version
import java.util.regex.Matcher
import java.util.regex.Pattern

fun String.splitVersion(): Version? {
    val versionPattern: Pattern = Pattern.compile("(\\d+)\\.(\\d+)(?:\\.(\\d+))?(?:[.-]([a-zA-Z0-9]+))?")
    val matcher: Matcher = versionPattern.matcher(this)

    if (matcher.matches()) {
        val majorVersion = matcher.group(1) + "." + matcher.group(2)
        val qualifier = matcher.group(4)
        val minorVersion = matcher.group(3)?.let { Integer.parseInt(matcher.group(3)) }
        return Version(this, majorVersion, minorVersion, qualifier)
    }
    return null
}