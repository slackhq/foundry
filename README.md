slack-gradle-plugin
===================

This repository contains the core Gradle plugin and associated logic used for Slack's Android app.

This repo is effectively _read-only_ and does not publish artifacts to Maven Central. We [develop
these in the open](https://slack.engineering/developing-in-the-open/) to knowledge-share with the community.

As such, our issue tracker is closed and we don't normally accept external PRs, but we welcome your
questions in the discussions section of the project!

We may later publish some of these components. If you're interested in this, feel free to raise in
a discussions post or vote for existing suggestions.

## Highlights

### Common project configuration

The `slack.base` plugin offers common configuration for all projects implementing it, covering a
wide spectrum of Android, Kotlin, and Java configurations.

This includes a whole host of things!
- Common Android configuration (single variant libraries, disabling unused features, compose, etc).
- Common Kotlin configuration (freeCompilerArgs, JVM target, etc).
- Common Java configuration (toolchains, release versions, etc).
- Common annotation processors.
- SlackExtension (see next section).
- Formatting (via Spotless).
- Platforms and BOM dependencies (see "Platform plugins" section below).
- Common lint checks (both on Android and plain JVM projects).

### Feature DSL

To ease use and configuration of common features in projects, we expose a `slack` DSL in projects
that allows for configuration of these in a semantically easy and boilerplate-free way. This is
controlled via `SlackExtension`.

```kotlin
slack {
  features {
    dagger()
  }
}
```

A major benefit of this is that we can intelligently configure features and avoid applying costly
plugins like Kapt unless they're actually needed for a specific feature, such as Java injections in
projects using Dagger.

### Platform plugins

`Platforms.kt` contains an implementation that sources a `VersionCatalog` and applies it to a
Gradle platform project. This allows us to effectively treat our versions catalog as a BOM and apply
it to all projects in the consuming repo and reduce dependency version stratification.

### Thermal throttling capture

Macbooks can suffer thermal throttling and result in poor build performance. We built
instrumentation for this to capture these and include them in our build scans to better understand
their impact. We support both Intel and Apple Silicon macs now and contain this implementation in
`ThermalsParser.kt`. This also includes helpful charting APIs for visualizing the data (courtesy
of our friends at Square).

### Dependency Rake

`DependencyRake.kt` contains an extension to the `gradle-dependency-analysis-plugin` that applies
its advice to a project to automatically optimize it and _rake_ dependencies.

### Module Stats (aka "Mod Score")

As a part of our modularization efforts, we developed a scoring mechanism for modules that we could
use as a measure of their "modularization". This includes a number of metrics and weighs them in a
formula to compute a score. This includes LoC, language mixtures, and build graph centrality. This
logic is under the `slack.stats` package.

### Robolectric Jars Management

Robolectric uses preinstrumented Android API jars that live on maven central. While it can handle
downloading of these automatically, we found this implementation to be brittle and unreliable, so we
built our own version of it that handles downloading these into a local `.cache` directory. This
implementation lives in `UpdateRobolectricJarsTask.kt` and that task is configured to be a
dependency of all `Test` tasks.

### Bootstrap

We try to simplify and streamline the bootstrap process for both local development and on CI. This
involves computing optimized JVM arguments for the Gradle and Kotlin daemons (which differ between
CI and local) as well as toe-holds for future customizations.

### Permission Checks

To avoid accidentally checking in any new, unexpected manifest permissions, we have a
`CheckManifestPermissionsTask` that compares the final merged manifest's permissions to an
allow list of known permissions. This is allow list is checked in and expected to be guarded by a
CODEOWNER watch and will fail the build if they differ.

```kotlin
slack {
  android {
    app {
      permissionAllowlist {
        if (name == "externalRelease") {
          setAllowlistFile(file("permissionsAllowlist.txt"))
        }
      }
    }
  }
}
```

### APK Versioning Computers

AGP offers new property-based APIs for computing APK version codes and version names. We use this
to compute information from different inputs (CI build number, git state, etc) and control this
logic in `ApkVersioningPLugin.kt`.

### Check dependency versions

Sometimes a dependency update may bring with it a surprise update to a transitive dependency that
we also declare. In order to avoid this happening unexpectedly, the `CheckDependencyVersionsTask`
checks that any transitive dependency versions that also correspond to a version declared in our
`VersionCatalog` match the version there. It's ok if they don't, but the author just need to update
the version in the catalog too to be explicit (or investigate further if it's an unwanted
surprise!).

### AGP Handlers

AGP occasionally contains new or breaking API changes that we want to handle gracefully in early
testing. We [regularly test against newer preview versions of AGP][shadow-jobs] so we can't just
hardcode in new APIs and expect them to work everywhere. To handle this, we have an `AgpHandler`
interface that can be used to abstract these new APIs in a backward-compatible way. Then we ship
implementations of this as different artifacts that are built against different AGP versions. Then,
at runtime, we pick the appropriate instance (via service loading) to use for the current AGP
version being used in that build.

### Detekt baselining

Detekt is a static analysis tool that we use to check for common issues in our code. We use one
global baseline file for baselined issues (when introducing new checks or updates), but Detekt
doesn't currently support this easily. So, we built `MergeDetektBaselinesTask` to merge all the
generated baselines from each subproject into a single global baseline.

License
--------

    Copyright 2022 Slack Technologies, LLC

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

[shadow-jobs]: https://slack.engineering/shadow-jobs/
