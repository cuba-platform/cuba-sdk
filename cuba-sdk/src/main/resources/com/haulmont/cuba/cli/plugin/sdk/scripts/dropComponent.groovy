///*
// * Copyright (c) 2008-2019 Haulmont.
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *     http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//
package com.haulmont.cuba.cli.plugin.sdk.scripts

import groovy.json.JsonSlurper
import org.sonatype.nexus.repository.storage.StorageFacet

def sdkConfig = new JsonSlurper().parseText(args)

def repositoryId = sdkConfig.repoName
def artifact = sdkConfig.artifact

def split = artifact.split(':')
def groupId = split[0]
def artifactId = split[1]
def version = split[2]

def repo = repository.repositoryManager.get(repositoryId);
StorageFacet storageFacet = repo.facet(StorageFacet);
def tx = storageFacet.txSupplier().get();

tx.begin();
tx.findComponents(
        Query.builder().where('group = ').param(groupId).and('name = ').param(artifactId).and('version = ').param(version).build(),
        [repo]
).forEach {
    tx.deleteComponent(it)
}
tx.commit();
return true