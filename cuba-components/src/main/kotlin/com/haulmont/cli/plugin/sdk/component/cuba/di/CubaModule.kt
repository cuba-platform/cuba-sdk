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

package com.haulmont.cli.plugin.sdk.component.cuba.di

import com.haulmont.cli.plugin.sdk.component.cuba.services.cuba.CubaComponentVersionManager
import com.haulmont.cli.plugin.sdk.component.cuba.services.cuba.CubaComponentVersionManagerImpl
import com.haulmont.cli.plugin.sdk.component.cuba.services.jmix.JmixComponentVersionManager
import com.haulmont.cli.plugin.sdk.component.cuba.services.jmix.JmixComponentVersionManagerImpl
import com.haulmont.cuba.cli.plugin.sdk.di.sdkKodein
import org.kodein.di.Kodein
import org.kodein.di.generic.bind
import org.kodein.di.generic.singleton

val cubaComponentModule = Kodein.Module {
    bind<CubaComponentVersionManager>() with singleton {
        CubaComponentVersionManagerImpl()
    }

    bind<JmixComponentVersionManager>() with singleton {
        JmixComponentVersionManagerImpl()
    }
}

val cubaComponentKodein = Kodein {
    extend(sdkKodein)
    import(cubaComponentModule)
}


