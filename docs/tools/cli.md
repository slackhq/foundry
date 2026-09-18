# Foundry CLIs

An artifact containing basic CLI utilities for Kotlin.

## Installation

[![Maven Central](https://img.shields.io/maven-central/v/com.slack.foundry/cli.svg)](https://mvnrepository.com/artifact/com.slack.foundry/cli)
```kotlin
// In Gradle
dependencies {
  implementation("com.slack.foundry:cli:<version>")
}

// In kotlin script
@file:DependsOn("com.slack.foundry:cli:{version}")
```

## Building

This project uses Gradle's isolated projects feature by default. The CLI module uses KSP
(Kotlin Symbol Processing) for `@AutoService` annotations to generate service loader files for
command discovery:

```bash
./gradlew :tools:cli:build
```

To explicitly verify that the packaged CLI contains every expected service-loader entry, run:

```bash
tools/scripts/verify-cli-service-loader.sh
```

## Local testing

If consuming these utilities from a kotlin script file, you can test changes like so:

1. Set the version in `gradle.properties`, such as `2.5.0-LOCAL1`.
2. Run `./gradlew publishToMavenLocal` to publish the current version to your local maven repository.
3. In your script file, add the local repository and update the version:
    ```kotlin
    @file:Repository("file:///Users/{username}/.m2/repository")
    @file:DependsOn("com.slack.foundry:cli:{version you set in gradle.properties}")
    ```
4. Repeat as needed while testing, incrementing the version number each time to avoid caching issues.
