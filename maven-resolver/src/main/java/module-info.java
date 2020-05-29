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

import com.haulmont.cli.core.CliPlugin;
import com.haulmont.cli.plugin.sdk.maven.MavenResolverPlugin;
import com.haulmont.cuba.cli.plugin.sdk.services.ArtifactManager;

module com.haulmont.cuba.cli.plugin.sdk.maven {
    requires java.base;

    requires kotlin.stdlib;
    requires kotlin.reflect;

    requires jcommander;

    requires com.google.common;
    requires kodein.di.core.jvm;
    requires kodein.di.generic.jvm;
    requires com.haulmont.cli.core;
    requires com.haulmont.cuba.cli.plugin.sdk;
    requires gson;
    requires java.logging;
    requires fuel;
    requires maven.model;
    requires kotlin.xml.builder;

    opens com.haulmont.cli.plugin.sdk.maven;
    opens com.haulmont.cli.plugin.sdk.maven.di;

    provides CliPlugin with MavenResolverPlugin;

    provides ArtifactManager with com.haulmont.cli.plugin.sdk.maven.MvnArtifactManagerImpl;
    uses ArtifactManager;
}