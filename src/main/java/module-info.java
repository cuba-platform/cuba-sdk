/*
 * Copyright (c) 2008-2018 Haulmont.
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

import com.haulmont.cuba.cli.CliPlugin;

module com.haulmont.cuba.cli.plugin.sdk {
    requires java.base;
    requires kotlin.stdlib;
    requires kotlin.stdlib.common;
    requires kotlin.reflect;

    requires jcommander;

    requires com.haulmont.cuba.cli;
    requires com.google.common;
    requires kodein.di.core.jvm;
    requires kodein.di.generic.jvm;
    requires practicalxml;
    requires fuel;
    requires maven.model;
    requires gson;
    requires result;
    requires org.json;
    requires java.logging;
//    requires kotlin.xml.builder;
    requires gradle.tooling.api;
    requires java.management;
    requires java.desktop;
    requires mapdb;
    requires kotlin.xml.builder;

    opens com.haulmont.cuba.cli.plugin.sdk;
    opens com.haulmont.cuba.cli.plugin.sdk.dto;
    opens com.haulmont.cuba.cli.plugin.sdk.commands;
    opens com.haulmont.cuba.cli.plugin.sdk.commands.artifacts;
    opens com.haulmont.cuba.cli.plugin.sdk.commands.repository;

    exports com.haulmont.cuba.cli.plugin.sdk;

    provides CliPlugin with com.haulmont.cuba.cli.plugin.sdk.SdkPlugin;
}