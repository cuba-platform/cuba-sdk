<p>
<a href="http://www.apache.org/licenses/LICENSE-2.0"><img src="https://img.shields.io/badge/license-Apache%20License%202.0-blue.svg?style=flat" alt="license" title=""></a>
</p>

# CUBA SDK

- [Overview](#overview) 
- [Installation](#installation)
- [Configuration](#configuration)
- [Commands Reference](#commands-reference)
  - [Common SDK Commands](#common-sdk-commands)
  - [Embedded Nexus Repository Commands](#embedded-nexus-repository-commands)
  - [Manage Repositories](#manage-repositories)
  - [Manage Components](#manage-components)
- [SDK Settings](#sdk-settings)

# 1. Overview <a name="overview"></a>

CUBA SDK is a command-line tool that provides an ability to resolve and export all dependencies 
for CUBA framework, add-ons or any external library and then use SDK as an embedded repository with 
few simple commands. This tool has a built-in [Nexus 3 repository](https://www.sonatype.com/nexus-repository-oss). 

CUBA SDK is a useful tool if it is required to develop applications with limited network environment. 


### Main CUBA SDK features
- IDE-Agnostic command line tool.
- Can be installed on CI server.
- Automatically collect and resolve all artifact dependencies.
- User friendly command line interface.
- Supports external plugins.
- Using Gradle to resolve artifact dependencies.
- Checks for new artifacts versions and can install them automatically with all dependencies.
- Can work with few a source and target repositories.
- Supports local `m2` repository as source or target repository.
- Can install and setup embedded Nexus OSS repository.
- Import/Export resolved artifacts with dependencies.
- Integrated with CUBA addons marketplace. 
- Supports difeerent profiles for one CUBA SDK instance. 

# 2. Installation <a name="installation"></a>

Tool will be available in SDK MAN repository soon. 

# 3. Configuration <a name="configuration"></a>

SDK should be configured before the first usage. To configure SDK run the `init` command. 

# 4. Commands Reference <a name="commands-reference"></a>

## 4.1. Common SDK Commands <a name="common-sdk-commands"></a>

- `sdk` - prints current SDK status.
- `properties` - prints configured SDK properties. Specific properties can be printed with `--n` or `--name` additional parameters, for example,  `properties --n sdk.export.path`
- `init` - inits SDK. This command configures SDK properties and downloads, installs and configures *Gradle*. For the already configured SDK, this command does not clean up current SDK metadata.
- `setup-nexus` - sets up embedded Nexus repository. This command downloads, installs and configures Nexus repository.
- `cleanup` - cleans up SDK metadata and remove all artifacts from the local *m2* repository and the embedded Nexus repository. If `--local-only` flag is provided, then only the local *m2* repository will be cleaned.
- `set-license` - sets the license key and configures Premium repositories for the *source* repository. 
- `check-updates` - checks available minor updates for framework and add-ons.  Specific target repository can be configured with `--r` or `--repository` additional parameters, for example, `import --r sdk2`. If `--no-upload` additional parameter is presented, then SDK archive will be imported only to the local *m2* repository.

## 4.2. Embedded Nexus Repository Commands <a name="embedded-nexus-repository-commands"></a>

- `start` - starts embedded repository.
- `stop` - stops embedded repository. 

## 4.3. Manage Repositories <a name="manage-repositories"></a>

SDK tool has three repository scopes:
- **search** - repository to search components for CUBA frameworks and add-ons. 
- **source** - source repository for components. Dependencies will be downloaded from these repositories.  
- **target** - target repository to upload components with dependencies.

By default the following repositories are configured:
- **search scope:**
  - Local `m2` repository
  - CUBA Bintray
  - CUBA Nexus
- **source scope:**
  - Local `m2`
  - Jcenter
  - Java central
  - CUBA Bintray
  - CUBA Nexus   
- **sdk scope:**
  - repository configured in `setup` command 
    
**Commands:**
- `repository list` - prints list of configured repositories.
- `repository list target` - prints list of configured target repositories.
- `repository list source` - prints list of configured source repositories.
- `repository list search` - prints list of configured search repositories.

- `repository add` - configures new repository.
- `repository add target` - configures new target repository.
- `repository add source` - configures new source repository.
- `repository add search` - configures new search repository.

- `repository remove` - removes repository.
- `repository remove target` - removes target repository.
- `repository remove source` - removes source repository.
- `repository remove search` - removes search repository.

## 4.4. Manage Components <a name="manage-components"></a>

### Component Commands 

List command prints a list of resolved and installed components:
- `list cuba`
- `list addon`
- `list lib`

Component coordinates for framework and add-on component commands can be configured as:
- `empty` - asks which framework or add-on should be installed. User can select a name and version from the list.
- `<name>` - searches the component by *name* and select version from the versions list.
- `<name>:<version>` - searches component by *name* and runs command for the component for the configured version.
- `<group>:<name>:<version>` - runs command for the component by full component coordinates.

Example: `push cuba 7.1.3`
 
Resolve command finds and downloads all component dependencies to local Gradle cache. If an add-on depends on other add-ons, then SDK will ask to resolve additional add-ons too. This feature can be disabled with `--nra` or `--not-resolve-addons` additional parameters. 
- `resolve` - bulk command for the list of frameworks, add-ons, and libs.
- `resolve cuba`
- `resolve addon`
- `resolve lib`

Push command uploads resolved components with dependencies to all *target* repositories. Specific target repository can be configured with `--r` or `--repository` additional parameters, for example, `push addon dashboard --r sdk2`.
- `push` - bulk command for the list of frameworks, add-ons, and libs.  
- `push cuba`
- `push addon <name>`
- `push lib`

Install command resolves and pushes components. Specific target repository can be configured with `--r` or `--repository` additional parameters, for example, `push addon dashboard --r sdk2`.
- `install` - bulk command for the list of frameworks, add-ons, and libs. 
- `install cuba`
- `install addon`
- `install lib`

Remove command removes the component with dependencies from the local *m2* repository and the embedded Nexus repository. If `--local-only` flag is provided, then the component will be removed only from the local *m2* repository.  
- `remove cuba`
- `remove addon`
- `remove lib`

Component coordinates for bulk commands can be passed with ','. For example: `install -c framework-cuba:7.2.1,addon-dashboard:3.2.1`.

Export command exports the component with dependencies as an archive to the `sdkproperties[sdk.export.home]` directory. If the component is not resolved yet, then SDK will ask to resolve the component.  
- `export` - exports all resolved SDK components.
- `export cuba`
- `export addon`
- `export lib`
    
Import command imports exported SDK archive to the current SDK and upload it to *sdk* repositories. Specific target repository can be configured with `--r` or `--repository` additional parameters, for example, `import --r sdk2`. If the `--no-upload` additional parameter is presented, then SDK archive will be imported only to the local *m2* repository.
- `import <file path>` 

### Additional Parameters which can be Applied to Components Commands:
- `--f` or `--force` - resolves and uploads the component with dependencies even if the component is already resolved or installed.
- `--single` - runs the command in the single-thread mode.
- `--info` - prints Gradle output. Please note, that in this case the command will be executed in the single-thread mode.
- `--go` or `--gradle-option` - additional Gradle execution options.

# 5. SDK Settings <a name="sdk-settings"></a>

Configured SDK settings by default are located in the `<User.home>/cli/sdk/sdk.properties` file. Current configured settings can be printed with `properties` command.

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

*Local repo settings*
- `maven.local.repo` - local *m2* repository folder path. This folder using for components import.

*Gradle settings*
- `gradle.home` - gradle home folder
- `gradle.cache` - gradle cache folder
- `gradle.version` - gradle version

### Apply Custom SDK Settings

Following parameters can be applied to all commands:
- `--s` or `--settings` - path to the custom settings file. All settings from this file override the default setting properties. This feature can be useful to create SDK profiles.
- `--sp` or `--setting-property` override default setting parameter, for example `--sp maven.local.repo=/home/user/other-m2`.




