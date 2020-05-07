<p>
<a href="http://www.apache.org/licenses/LICENSE-2.0"><img src="https://img.shields.io/badge/license-Apache%20License%202.0-blue.svg?style=flat" alt="license" title=""></a>
</p>

# CUBA SDK

- [Overview](#overview) 
- [Installation](#installation)
  - [Manual Installation from Sources](#manual-installation-from-sources)
- [Configuration](#configuration)
- [Commands Reference](#commands-reference)
  - [Common SDK Commands](#common-sdk-commands)
  - [Embedded Nexus Repository Commands](#embedded-nexus-repository-commands)
  - [Manage Repositories](#manage-repositories)
  - [Manage Components](#manage-components)
- [SDK Settings](#sdk-settings)

# 1. Overview <a name="overview"></a>

CUBA SDK is a plugin for [CUBA CLI](https://github.com/cuba-platform/cuba-cli). This is a command-line tool that provides an ability to resolve and export all dependencies for CUBA framework, add-ons or any external library and then use SDK as an embedded repository. 
All resolved dependencies will be stored in the local SDK maven repository. This tool has a built-in [Nexus 3 repository](https://www.sonatype.com/nexus-repository-oss). 

# 2. Installation <a name="installation"></a>

CUBA CLI and SDK compatibility:

| CUBA CLI Version | SDK Version    |
|------------------|----------------|
| 2.1.1            | 0.1-SNAPSHOT   |  

## 2.1. Manual Installation from Sources <a name="manual-installation-from-sources"></a>

1. Install compatible CUBA CLI tool according to the [instruction](https://github.com/cuba-platform/cuba-cli/wiki/Installation).
2. Checkout CUBA SDK sources from [GitHub](https://github.com/cuba-platform/cuba-sdk).
3. Run `installPlugin` gradle task.
4. Run `cuba-cli` in the command line. 

If CUBA SDK plugin was loaded successfully then the following message will be displayed in the command line:

![plugin_loaded](img/plugin_loaded.png)

# 3. Configuration <a name="configuration"></a>

SDK should be configured before the first usage. To configure SDK run the `sdk init` command. 

# 4. Commands Reference <a name="commands-reference"></a>

## 4.1. Common SDK Commands <a name="common-sdk-commands"></a>

- `sdk` - prints current SDK status.
- `sdk properties` - prints configured SDK properties. Specific properties can be printed with `--n` or `--name` additional parameters, for example, `sdk properties --n sdk.export.path`
- `sdk init` - inits SDK. This command configures SDK properties and downloads, installs and configures *Gradle*. For the already configured SDK, this command does not clean up current SDK metadata.
- `sdk setup-nexus` - sets up embedded Nexus repository. This command downloads, installs and configures Nexus repository.
- `sdk cleanup` - cleans up SDK metadata and remove all artifacts from the local *m2* repository and the embedded Nexus repository. If `--local-only` flag is provided, then only the local *m2* repository will be cleaned.
- `sdk set-license` - sets the license key and configures Premium repositories for the *source* repository. 
- `sdk check-updates` - checks available minor updates for framework and add-ons.  Specific target repository can be configured with `--r` or `--repository` additional parameters, for example, `sdk import --r sdk2`. If `--no-upload` additional parameter is presented, then SDK archive will be imported only to the local *m2* repository.

## 4.2. Embedded Nexus Repository Commands <a name="embedded-nexus-repository-commands"></a>

- `sdk start` - starts embedded repository.
- `sdk stop` - stops embedded repository. 

## 4.3. Manage Repositories <a name="manage-repositories"></a>

SDK tool has three repository scopes:
- **search** - repository to search components for frameworks and add-ons. 
- **source** - source repository for maven commands. Dependencies will be downloaded from these repositories.  
- **target** - target repository to upload components with dependencies.

By default the following repositories are configured:
- **search scope:**
  - Local `m2` repository
  - CUBA Bintray
  - CUBA Nexus
- **source scope:**
  - Local `m2` repository
  - CUBA Bintray
  - CUBA Nexus   
- **sdk scope:**
  - repository configured in `setup` command 
    
**Commands:**
- `sdk repository list` - prints list of configured repositories.
- `sdk repository list target` - prints list of configured target repositories.
- `sdk repository list source` - prints list of configured source repositories.
- `sdk repository list search` - prints list of configured search repositories.

- `sdk repository add` - configures new repository.
- `sdk repository add target` - configures new target repository.
- `sdk repository add source` - configures new source repository.
- `sdk repository add search` - configures new search repository.

- `sdk repository remove` - removes repository.
- `sdk repository remove target` - removes target repository.
- `sdk repository remove source` - removes source repository.
- `sdk repository remove search` - removes search repository.

## 4.4. Manage Components <a name="manage-components"></a>

### Component Commands 

List command prints a list of resolved and installed components:
- `sdk list cuba`
- `sdk list addon`
- `sdk list lib`

Component coordinates for framework and add-on component commands can be configured as:
- `empty` - asks which framework or add-on should be installed. User can select a name and version from the list.
- `<name>` - searches the component by *name* and select version from the versions list.
- `<name>:<version>` - searches component by *name* and runs command for the component for the configured version.
- `<group>:<name>:<version>` - runs command for the component by full component coordinates.

Example: `sdk push cuba 7.1.3`
 
Resolve command finds and downloads all component dependencies to local Gradle cache. If an add-on depends on other add-ons, then SDK will ask to resolve additional add-ons too. This feature can be disabled with `--nra` or `--not-resolve-addons` additional parameters. 
- `sdk resolve` - bulk command for the list of frameworks, add-ons, and libs.
- `sdk resolve cuba`
- `sdk resolve addon`
- `sdk resolve lib`

Push command uploads resolved components with dependencies to all *target* repositories. Specific target repository can be configured with `--r` or `--repository` additional parameters, for example, `sdk push addon dashboard --r sdk2`.
- `sdk push` - bulk command for the list of frameworks, add-ons, and libs.  
- `sdk push cuba`
- `sdk push addon <name>`
- `sdk push lib`

Install command resolves and pushes components. Specific target repository can be configured with `--r` or `--repository` additional parameters, for example, `sdk push addon dashboard --r sdk2`.
- `sdk install` - bulk command for the list of frameworks, add-ons, and libs. 
- `sdk install cuba`
- `sdk install addon`
- `sdk install lib`

Remove command removes the component with dependencies from the local *m2* repository and the embedded Nexus repository. If `--local-only` flag is provided, then the component will be removed only from the local *m2* repository.  
- `sdk remove cuba`
- `sdk remove addon`
- `sdk remove lib`

Component coordinates for bulk commands can be passed with ','. For example: `sdk install -c framework-cuba:7.2.1,addon-dashboard:3.2.1`.

Export command exports the component with dependencies as an archive to the `sdkproperties[sdk.export.home]` directory. If the component is not resolved yet, then SDK will ask to resolve the component.  
- `sdk export` - exports all resolved SDK components.
- `sdk export cuba`
- `sdk export addon`
- `sdk export lib`
    
Import command imports exported SDK archive to the current SDK and upload it to *sdk* repositories. Specific target repository can be configured with `--r` or `--repository` additional parameters, for example, `sdk import --r sdk2`. If the `--no-upload` additional parameter is presented, then SDK archive will be imported only to the local *m2* repository.
- `sdk import <file path>` 

### Additional Parameters which can be Applied to Components Commands:
- `--f` or `--force` - resolves and uploads the component with dependencies even if the component is already resolved or installed.
- `--single` - runs the command in the single-thread mode.
- `--info` - prints Gradle output. Please note, that in this case the command will be executed in the single-thread mode.
- `--go` or `--gradle-option` - additional Gradle execution options.

# 5. SDK Settings <a name="sdk-settings"></a>

Configured SDK settings by default are located in the `<User.home>/cli/sdk/sdk.properties` file. Current configured settings can be printed with `sdk properties` command.

### Properties Reference:

*Default SDK target repository which was configured in the `setup` command*
- `repository.type` - a type of the configured repository, can be `local` or `remote`.
- `repository.url` - repository URL, for embedded Nexus this property will point to nexus Web UI.
- `repository.name` - repository name.
- `repository.path` - path, where embedded Nexus repository is installed.
- `repository.login -  repository user login.
- `repository.password` - repository user password.

*SDK metadata*
- `sdk.home` - default SDK home directory.
- `sdk.export` - path to the directory to save exported SDK archives.

*Maven settings*
- `maven.local.repo` - local *m2* repository folder path.
- `maven.settings` - generated maven settings file path.
- `maven.path` - installed maven path.

### Apply Custom SDK Settings

Following parameters can be applied to all `sdk` commands:
- `--s` or `--settings` - path to the custom settings file. All settings from this file override the default setting properties. This feature can be useful to create SDK profiles.
- `--sp` or `--setting-property` override default setting parameter, for example `--sp maven.local.repo=/home/user/other-m2`.




