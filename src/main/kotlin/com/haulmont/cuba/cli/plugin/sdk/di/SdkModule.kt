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

package com.haulmont.cuba.cli.cubaplugin.di

import com.haulmont.cuba.cli.kodein
import com.haulmont.cuba.cli.plugin.sdk.nexus.NexusManager
import com.haulmont.cuba.cli.plugin.sdk.nexus.NexusManagerImpl
import com.haulmont.cuba.cli.plugin.sdk.nexus.NexusScriptManager
import com.haulmont.cuba.cli.plugin.sdk.nexus.NexusScriptManagerImpl
import com.haulmont.cuba.cli.plugin.sdk.services.*
import org.kodein.di.Kodein
import org.kodein.di.generic.bind
import org.kodein.di.generic.singleton

private val sdkModule = Kodein.Module {

    bind<SdkSettingsHolder>() with singleton {
        SdkSettingsHolderImpl()
    }

    bind<FileDownloadService>() with singleton {
        FileDownloadServiceImpl()
    }

    bind<ArtifactManager>() with singleton {
        GradleArtifactManagerImpl()
    }

    bind<MavenExecutor>() with singleton {
        MavenExecutorImpl()
    }

    bind<ComponentManager>() with singleton {
        ComponentManagerImpl()
    }

    bind<MetadataHolder>() with singleton {
        MetadataHolderImpl()
    }

    bind<ComponentTemplates>() with singleton {
        ComponentTemplatesImpl()
    }

    bind<RepositoryManager>() with singleton {
        RepositoryManagerImpl()
    }

    bind<ComponentVersionManager>() with singleton {
        ComponentVersionManagerImpl()
    }

    bind<NexusManager>() with singleton {
        NexusManagerImpl()
    }

    bind<NexusScriptManager>() with singleton {
        NexusScriptManagerImpl()
    }

    bind<ImportExportService>() with singleton {
        ImportExportServiceImpl()
    }
}

internal val sdkKodein = Kodein {
    extend(kodein)
    import(sdkModule)
}
