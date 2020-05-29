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

import com.haulmont.cli.core.MainCliPlugin;
import com.haulmont.cuba.cli.plugin.sdk.SdkPlugin;
import org.jline.terminal.spi.JansiSupport;

module com.haulmont.cuba.cli.plugin.sdk {
    requires java.base;

    requires kotlin.stdlib.jdk8;
    requires kotlin.stdlib.jdk7;
    requires kotlin.stdlib;
    requires kotlin.reflect;

    requires com.haulmont.cli.core;

    requires jcommander;

    requires com.google.common;
    requires kodein.di.core.jvm;
    requires kodein.di.generic.jvm;
    requires fuel;
    requires maven.model;
    requires gson;
    requires result;
    requires org.json;
    requires java.logging;
//    requires kotlin.xml.builder;
    requires java.management;
    requires java.desktop;
    requires mapdb;
    requires kotlin.xml.builder;
    requires velocity;
    requires jline;
    requires commons.lang;
    requires org.apache.commons.compress;

    opens com.haulmont.cuba.cli.plugin.sdk;
    opens com.haulmont.cuba.cli.plugin.sdk.dto;
    opens com.haulmont.cuba.cli.plugin.sdk.dto.spring;
    opens com.haulmont.cuba.cli.plugin.sdk.commands;
    opens com.haulmont.cuba.cli.plugin.sdk.commands.artifacts;
    opens com.haulmont.cuba.cli.plugin.sdk.commands.repository;
    opens com.haulmont.cuba.cli.plugin.sdk.di;
    opens com.haulmont.cuba.cli.plugin.sdk.services;
    opens com.haulmont.cuba.cli.plugin.sdk.event;

    exports com.haulmont.cuba.cli.plugin.sdk.dto;
    exports com.haulmont.cuba.cli.plugin.sdk.commands;
    exports com.haulmont.cuba.cli.plugin.sdk.commands.artifacts;
    exports com.haulmont.cuba.cli.plugin.sdk.commands.repository;
    exports com.haulmont.cuba.cli.plugin.sdk.di;
    exports com.haulmont.cuba.cli.plugin.sdk.services;
    exports com.haulmont.cuba.cli.plugin.sdk;
    exports com.haulmont.cuba.cli.plugin.sdk.event;
    exports com.haulmont.cuba.cli.plugin.sdk.utils;
    exports com.haulmont.cuba.cli.plugin.sdk.db;

    provides MainCliPlugin with SdkPlugin;

    uses com.haulmont.cuba.cli.plugin.sdk.SdkPlugin;
    uses com.haulmont.cuba.cli.plugin.sdk.services.ArtifactManager;

    //    jansi support workaround
    provides JansiSupport with com.haulmont.cuba.cli.plugin.sdk.JansiSupportWorkAround;
}