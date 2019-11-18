/*
 * Copyright (c) 2008-2019 Haulmont.
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

package com.haulmont.cuba.cli.plugin.sdk.commands.artifacts

import com.beust.jcommander.Parameters
import com.haulmont.cuba.cli.commands.AbstractCommand
import org.kodein.di.generic.instance
import java.io.PrintWriter

@Parameters(commandDescription = "List artifacts in SDK")
class ListCommand : AbstractCommand() {

    private val printWriter: PrintWriter by kodein.instance()

    override fun run() {
        printWriter.println("Use 'framework', 'addon' or 'lib' subcommands.")
    }
}