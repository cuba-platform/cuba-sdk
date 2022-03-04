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
import org.sonatype.nexus.blobstore.api.BlobStoreManager
import org.sonatype.nexus.repository.maven.LayoutPolicy
import org.sonatype.nexus.repository.maven.VersionPolicy
import org.sonatype.nexus.repository.storage.WritePolicy

import org.sonatype.nexus.security.user.UserNotFoundException;

def sdkConfig = new JsonSlurper().parseText(args)

def login = sdkConfig.login
def password = sdkConfig.password
def repoName = sdkConfig.repoName

security.setAnonymousAccess(false)
log.info('Anonymous access disabled')

try {
    security.getSecuritySystem().getUser(login)
    security.getSecuritySystem().changePassword(login, password)
    log.info("User $login updated")
} catch(UserNotFoundException ignored) {
    security.addUser(login, login, '', '', true, password, ['nx-admin'])
    log.info("User $login created")
}

if (repository.getRepositoryManager().get(repoName) == null) {
    repository.createMavenHosted(repoName, BlobStoreManager.DEFAULT_BLOBSTORE_NAME, true, VersionPolicy.MIXED,
            WritePolicy.ALLOW, LayoutPolicy.STRICT)
    log.info("Repository $repoName created")
} else {
    log.info("Repository $repoName already created")
}

return groovy.json.JsonOutput.toJson([result: 'SDK configuration completed'])