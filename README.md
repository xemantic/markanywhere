# xemantic-project-template

A template repository for Xemantic's Kotlin multiplatform projects

[//]: # (TODO replace title and description)

[//]: # (TODO for the shileds below, replace com.xemantic.template group and xemantic-project-template artifactId)

[<img alt="Maven Central Version" src="https://img.shields.io/maven-central/v/com.xemantic.template/xemantic-project-template">](https://central.sonatype.com/artifact/com.xemantic.template/xemantic-project-template)
[<img alt="GitHub Release Date" src="https://img.shields.io/github/release-date/xemantic/xemantic-project-template">](https://github.com/xemantic/xemantic-project-template/releases)
[<img alt="license" src="https://img.shields.io/github/license/xemantic/xemantic-project-template?color=blue">](https://github.com/xemantic/xemantic-project-template/blob/main/LICENSE)

[<img alt="GitHub Actions Workflow Status" src="https://img.shields.io/github/actions/workflow/status/xemantic/xemantic-project-template/build-main.yml">](https://github.com/xemantic/xemantic-project-template/actions/workflows/build-main.yml)
[<img alt="GitHub branch check runs" src="https://img.shields.io/github/check-runs/xemantic/xemantic-project-template/main">](https://github.com/xemantic/xemantic-project-template/actions/workflows/build-main.yml)
[<img alt="GitHub commits since latest release" src="https://img.shields.io/github/commits-since/xemantic/xemantic-project-template/latest">](https://github.com/xemantic/xemantic-project-template/commits/main/)
[<img alt="GitHub last commit" src="https://img.shields.io/github/last-commit/xemantic/xemantic-project-template">](https://github.com/xemantic/xemantic-project-template/commits/main/)

[<img alt="GitHub contributors" src="https://img.shields.io/github/contributors/xemantic/xemantic-project-template">](https://github.com/xemantic/xemantic-project-template/graphs/contributors)
[<img alt="GitHub commit activity" src="https://img.shields.io/github/commit-activity/t/xemantic/xemantic-project-template">](https://github.com/xemantic/xemantic-project-template/commits/main/)
[<img alt="GitHub code size in bytes" src="https://img.shields.io/github/languages/code-size/xemantic/xemantic-project-template">]()
[<img alt="GitHub Created At" src="https://img.shields.io/github/created-at/xemantic/xemantic-project-template">](https://github.com/xemantic/xemantic-project-template/commits)
[<img alt="kotlin version" src="https://img.shields.io/badge/dynamic/toml?url=https%3A%2F%2Fraw.githubusercontent.com%2Fxemantic%2Fxemantic-project-template%2Fmain%2Fgradle%2Flibs.versions.toml&query=versions.kotlin&label=kotlin">](https://kotlinlang.org/docs/releases.html)
[<img alt="discord users online" src="https://img.shields.io/discord/811561179280965673">](https://discord.gg/vQktqqN2Vn)
[![Bluesky](https://img.shields.io/badge/Bluesky-0285FF?logo=bluesky&logoColor=fff)](https://bsky.app/profile/xemantic.com)

## Why?

Creating new gradle projects, with all the conventions we are using at [Xemantic](https://xemantic.com), might be a hassle.

[//]: # (TODO replace with the rationale behind the new project)

[//]: # (TODO everything starting from here can be removed in your project)

## Usage

> [!NOTE]
> There is no value in using this project directly as a dependency, however the usage section is included, so you can replace it with your project dependency:

In `build.gradle.kts` add:

```kotlin
dependencies {
    implementation("com.xemantic.template:xemantic-project-template:0.3.0")
}
```

## How?

1. When creating new GitHub project choose this repository as a template
2. Follow the [CHECKLIST](CHECKLIST.md)

## Updating this template project

From time to time, it is worth to:

### Update gradlew wrapper

```shell
./gradlew wrapper --gradle-version 8.12.1 --distribution-type bin
```

### Update all the dependencies to the latest versions

All the gradle dependencies are managed by the [libs.versions.toml](gradle/libs.versions.toml) file in the `gradle` dir.

It is easy to check for the latest version by running:

```shell
./gradlew 
```
