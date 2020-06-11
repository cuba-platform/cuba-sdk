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

package com.haulmont.cuba.cli.plugin.sdk.event

import com.haulmont.cuba.cli.plugin.sdk.dto.Component
import com.haulmont.cuba.cli.plugin.sdk.dto.Repository

interface SdkEvent {

    /**
     * Fires after sdk was initiated
     */
    class SdkInitEvent : SdkEvent

    /**
     * Fires before component will be resolved. Provides component which will be resolved
     */
    class BeforeResolveEvent(val component: Component) : SdkEvent

    /**
     * Fires after component was resolved. Provides component which will be resolved
     */
    class AfterResolveEvent(val component: Component) : SdkEvent

    /**
     * Fires before component will be pushed to repository. Provides component and repositories
     */
    class BeforePushEvent(val component: Component, val repositories: List<Repository>) : SdkEvent

    /**
     * Fires after component was pushed to repository. Provides component and repositories
     */
    class AfterPushEvent(val component: Component, val repositories: List<Repository>) : SdkEvent

    /**
     * Fires before component will be removed. Provides component
     */
    class BeforeRemoveEvent(val component: Component, val removeFromRepository: Boolean) : SdkEvent

    /**
     * Fires after component was removed. Provides component and repository
     */
    class AfterRemoveEvent(val component: Component,val removeFromRepository: Boolean) : SdkEvent

    /**
     * Fires when new component versions found in check versions command. Provides component and new version
     */
    class NewVersionAvailableEvent(val component: Component, val update: Component) : SdkEvent

    /**
     * Fires before repository will be added. Provides repository
     */
    class BeforeAddRepositoryEvent(val repository: Repository) : SdkEvent

    /**
     * Fires after repository was added. Provides component and repository
     */
    class AfterAddRepositoryEvent(val repository: Repository) : SdkEvent

    /**
     * Fires before repository will be removed. Provides repository
     */
    class BeforeRemoveRepositoryEvent(val repository: Repository) : SdkEvent

    /**
     * Fires after repository was removed. Provides component and repository
     */
    class AfterRemoveRepositoryEvent(val repository: Repository) : SdkEvent

}