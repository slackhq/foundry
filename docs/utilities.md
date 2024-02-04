Utilities
=========

There are a bunch of miscellaneous utilities and tools in this project that don't necessarily warrant their own
dedicated docs page.

## `AndroidSourcesConfigurer`

When testing new Android SDK betas, the compile SDK version is available months before sources are. Developers want to
build against these APIs, but we don't want to make their experience in the IDE worse than necessary. The problem with
using a compile SDK version that doesn't have sources is that the IDE can't provide any documentation for the APIs and
will just show stub files instead.

To work mitigate this, we will _patch_ the SDK by putting a copy of the previous version's sources in the location of
the new SDK. This allows most sources to still index properly during the beta period. Then, once the new sources are
available, the consuming repo needs only to update the `slack.latestCompileSdkWithSources` gradle property to that new
SDK version and the patcher will clear out that copy and let AGP download the real ones.

This runs automatically in the root plugin.

## AndroidTest APK Aggregation

At Slack we use FTL + Fladle for running our instrumentation tests. In order to add more test APKs from modularized
instrumentation tests in other subprojects, we have to aggregate a list of their locations and pass them on to Fladle.
This is done via `AndroidTestApksTask`, which is registered in the root project and can be wired to pipe its output file
into Fladle's config input.

**Example**

```kotlin
val aggregatedApksProvider = rootProject.tasks
  .named<AndroidTestApksTask>("aggregateAndroidTestApks")
  .flatMap { it.outputFile }
  .map { it.asFile.readLines() }

tasks
  .withType<YamlConfigWriterTask>()
  .named { it == "writeConfigProps${fladleTarget}" }
  .configureEach { additionalTestApks.value(testInputsProvider) }
```

This task is automatically added to whenever a subproject uses
the `androidTest()` [DSL feature](/dsl/#android-test).

## `PermissionChecks`

Permissions are an integral part of Android apps, and oversight into what permissions are required in the app is
critical to a release pipeline. `PermissionChecks` is a feature to help with this.

The workflow we use at Slack is like this:

- We check in a `permissionsAllowlist.txt` file in the Slack android repo.
- This file is owned in GitHub by a specific codeowner rule that adds a permission reviewers group.
- This file is passed into the `allowListFile` [DSL feature](/dsl/#permission-allowlist) in the application
  project.
- On each build, SGP automatically checks that the permissions present in the release APK manifest match the ones
  defined in the allowlist.

This way new permissions are not accidentally or secretly added to the app.