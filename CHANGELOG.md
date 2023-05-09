Changelog
=========

0.9.5
-----

_2023-05-09_

- Use `disallowChanges()` where possible on properties SGP controls in order to avoid accidental overwrites.
- Make `ComputeAffectedProjectsTask` also generate a `affected_android_test_projects.txt` file with a newline-delimited list of affected projects that enable `androidTest()`. This can be used in CI scripts to statically determine if instrumentation tests need to run.

0.9.4
-----

_2023-05-06_

- Fix alias naming in `SlackVersions`. See `SlackVersions.kt` for updated expected naming of aliases.

0.9.3
-----

_2023-05-05_

- Add `jdk.compiler/com.sun.tools.javac.model` to Bootstrap Gradle JVM args and exec prefixes for binaries for GJF 17.

0.9.2
-----

_2023-05-05_

- Fix accidental noisy JVM vendor log.

0.9.1
-----

_2023-05-04_

Happy May the Fourth!

- Add new `sgp.config.jvmVendor` property to control the JVM vendor used in Kotlin and Java toolchains. This value is used to match a known vendor spec, such as `AZUL`.
- Apply the kover plugin in an `afterEvaluate` block to avoid https://github.com/Kotlin/kotlinx-kover/issues/362.
- Update jgrapht to 1.5.2.
- Update oshi to 6.4.2.

0.9.0
-----

_2023-04-30_

- Improve Skippy logging.
- Configure all Kotlin compilations, not just JVM compilations.
- Split standard JVM args and common Kotlin args.
- Simplify `OkHttpClient` setup in `SlackTools`.
- Update to Kotlin 1.8.21.
- Support Dagger KSP in `slack.features.dagger` DSL controls. There are two new properties to control this:
  - `slack.ksp.allow-dagger` – allow use of Dagger in KSP.
  - `slack.ksp.allow-anvil` – allow use of Anvil in KSP. Note this is not yet implemented in Anvil, just a toe-hold for the future.
- Add debugging logs for loading `SlackToolsExtension` instances + fix classloader used for it.
- Gracefully handle `SlackToolsExtension` extensions that fail to load.

0.8.10
-----

_2023-04-25_

- Add `Context` to `SlackToolsExtension`.

0.8.9
-----

_2023-04-25_

- Expose missing `SlackTools.findExtension` API.
- Expose missing `SlackTools.SERVICE_NAME` for `@ServiceReference` API.

0.8.8
-----

_2023-04-25_

- Update to Kotlin 1.8.20.
- Remove `moshi-kotlin`, only use generated adapters now.
- Don't auto-apply the Kover plugin on a platform project.
- Add new `sgp.ge.apply-common-build-tags` property flag to gate applying common build tags to a project.
- Switch `SlackToolsExtension` to work as a `ServiceLoader` instead.

0.8.7
-----

_2023-04-23_

- Remove lock file checking in `SlackTools` because this apparently invalidates configuration cache every time.

0.8.6
-----

_2023-04-22_

- Revert using native Kotlin lambdas instead of `class` for SAM conversions due to https://github.com/gradle/gradle/issues/24871.

0.8.5
-----

_2023-04-22_

- Clean up thermals logging setup in `SlackTools` and support enabling property at different scopes (local.properties, etc).
- Shut down thermals heartbeat executor when `SlackTools` is closed.
- Use native Kotlin lambdas instead of `class` for SAM conversions. The minimum supported Gradle version is now 8.1, which introduced support for this.

0.8.4
-----

_2023-04-22_

- Fix JSON serialization for thermals data.

0.8.3
-----

_2023-04-22_

- Don't accidentally create new `SlackTools` instances when reporting background data to Gradle Enterprise. These instances would be orphaned because this would happen _after_ Gradle had closed all existing services, and create a memory leak.
- Use a lock file to track `SlackTools` instances.
- Use a single-threaded `Executor` for `SlackTools`' thermals heartbeat.

0.8.2
-----

_2023-04-22_

- Log a `Throwable` with multiple instances of `SlackTools` to help track origin points.

0.8.1
-----

_2023-04-22_

- Add some debug logging to `SlackTools` to track multiple instances.

0.8.0
-----

_2023-04-15_

- **Fix**: Wrap all exec operations in a `ValueSource` for Gradle 8.x compatibility.
- **Fix**: Set git line endings to `PLATFORM_NATIVE` in spotless by default. Its default of looking at `.gitattributes` is expensive and incompatible with Gradle 8.1+ configuration caching.
- **Fix**: Add `slack.auto-apply.sort-dependencies` boolean Gradle property to gate auto-applying the sort-dependencies plugin.
- SGP now requires AGP 8.0+ (and with it – Gradle 8+).

0.7.9
-----

_2023-04-01_

Happy April Fool's Day!

- [Skippy] Recursively resolve project dependencies to avoid missing transitive edges in the graph. Previously we only computed shallow dependencies.

0.7.8
-----

_2023-03-28_

- **Fix**: Add missing `detekt` task dependencies for `globalDetekt`.
- **Fix**: Only apply detekt config once (even if multiple Kotlin plugins are applied).

0.7.7
-----

_2023-03-27_

- Add new `slack.detekt.full` property to gate whether or to run full detekt (i.e. with type resolution). If disabled, `detektRelease`/`detektMain` and associated tasks will be disabled and not used in `detektGlobal`.

0.7.6
-----

_2023-03-25_

- **Fix**: Apply matching configurations to `DetektCreateBaselineTask` tasks too due to https://github.com/detekt/detekt/issues/5940.

0.7.5
-----

_2023-03-24_

- [Skippy] Add more default configurations.
- [Skippy] Add `slack.avoidance.build-upon-default-affected-project-configurations` flag to make provided configurations build upon defaults.
- Add new `globalDetekt` task that runs `detekt` on all subprojects. This is Skippy-compatible and responds to `slack.avoidance.affectedProjectsFile`.

0.7.4
-----

_2023-03-22_

- Don't expose `androidExtension` publicly in `SlackExtension` to avoid Gradle mismatching number of type arguments in AGP 8.1.0-alpha10+.

0.7.3
-----

_2023-03-22_

- Set `Detekt.jdkHome` to null to avoid https://github.com/detekt/detekt/issues/5925.
- Rename `String.safeCapitalize()` to `String.capitalizeUS()` to make it more explicit.

0.7.2
-----

_2023-03-21_

- Disable Live Literals in Compose by default due to multiple issues. They can be enabled via `-Pslack.compose.android.enableLiveLiterals=true`.
  - Poor runtime performance: https://issuetracker.google.com/issues/274207650.
  - Non-deterministic class files breaking build cache: https://issuetracker.google.com/issues/274231394.
- [Skippy] Add `.github/actions/**` to default never skip filters.

0.7.1
-----

_2023-03-20_

- [Skippy] Improve pattern configuration.
  1. Make the default patterns public. This allows consumers to more
     easily reuse them when customizing their own.
  2. Use sets for the type to better enforce uniqueness requirements.
  3. Add github actions to never-skip defaults.
  4. Add excludePatterns to allow finer-grained control. This runs _after_
     include filtering so that users can manually exclude certain files that
     may otherwise be captured in an inclusion filter and is difficult to
     describe in a simple glob pattern. GitHub action does similar controls
     for CI matrices.
- [Skippy] Allow relative (from the project root) to `affected_projects.txt` and allow non-existent files as a value. This makes it easy to gracefully fall back in CI.
- [Skippy] Fix logging path matchers missing toString() impls.
- [SKippy] Log verbosely in debug mode when skipping task deps.
- Update oshi to `6.4.1`.

0.7.0
-----

_2023-03-17_

### Project Skippy

This release introduces an experimental new `computeAffectedProjects` task for computing affected projects based on an input of changed files. The goal of this is to statically detect which unit test, lint, and androidTest checks can be safely skipped in CI on pull requests.

Example usage
```bash
./gradlew computeAffectedProjects --changed-files changed_files.txt
```

Where `changed_files.txt` is resolved against the root repo directory and contains a newline-delimited list of changed files (usually inferred from a PR).

A simple example of how to produce such a file with the `gh` CLI:

```bash
gh pr view ${{ github.event.number }} --json files -q '.files[].path' > changed_files.txt
```

One would run this task _first_ as a preflight task, then run subsequent builds with the `slack.avoidance.affectedProjectsFile` Gradle property pointing to its output file location (printed at the end of the task).

```bash
./gradlew ... -Pslack.avoidance.affectedProjectsFile=/Users/zacsweers/dev/slack/slack-android-ng/build/skippy/affected_projects.txt
```

The `globalCiLint`, `globalCiUnitTest`, and `aggregateAndroidTestApks` tasks all support reading this property and will avoid adding dependencies on tasks in projects that are not present in this set.

The `ComputeAffectedProjectsTask` task has some sensible defaults, but can be configured further in the root projects like so.

```kotlin
tasks.named<ComputeAffectedProjectsTask>("computeAffectedProjects") {
  // Glob patterns of files to include in computing
  includePatterns.addAll(
    "**/*.kt",
    "**/*.java",
  )
  // Glob patterns of files that, if changed, should result in not skipping anything in the build
  neverSkipPatterns.addAll(
    "**/*.versions.toml",
    "gradle/wrapper/**",
  )
}
```

Debug logging can be enabled via the `slack.debug=true` Gradle property. This will output timings, logs, and diagnostics for the task.

The configurations used to determine the build graph can be customized via comma-separated list to the `slack.avoidance.affected-project-configurations` property.

0.6.1
-----

_2023-03-15_

Happy Ted Lasso season 3 premier day!

- **Fix**: Remove `UseContainerSupport` jvm arg from unit tests as this appears to only work on Linux.

0.6.0
-----

_2023-03-14_

Happy Pi day!

- Refactor how unit tests are configured.
  - `Test` tasks are now configured more consistently across CI and local, so there should be more cache hits.
  - Add a new `globalCiUnitTest` task to the root project to ease running `ciUnitTest` tasks across all subprojects.
  - Add new properties to `SlackProperties` for controlling max parallelism and `forkEvery` options in `Test` tasks.
- Refactor how lint tasks are configured.
  - Add a new `ciLint` task to every project that depends on all lint tasks in that project. This is intended to be the inverse
    behavior of the built-in `lint` task in Android projects, which only runs the default variant's lint task.
  - Add a new `globalCiLint` task to the root project to ease running `ciLint` tasks across all subprojects.
  - Add new properties to `SlackProperties` for controlling which variants should be linted.
- Revert "Add lintErrorRuleIds property". `lint.xml` is the right place for this kind of logic.

0.5.10
------

_2023-03-07_

- Reduce noisy JNA load failures logging. Still have not gotten to the root cause, but at least this will reduce the log noise.
- Add a new `slack.lint.severity.errorRuleIds` Gradle property to specify lint rule IDs that should always be error severity.

0.5.9
-----

_2023-02-27_

- Gracefully handle JNA load failures in thermals logging.

0.5.8
-----

_2023-02-20_

- **Enhancement**: Remove kotlin-dsl from the plugin. If you were indirectly relying on its APIs from this plugin, you'll need to add that dependency separately.
- **Enhancement**: Better support fully modularized lints
  - `checkDependencies` is no longer enabled by default.
  - Make the baseline file name configurable via `slack.lint.baseline-file-name` property. Defaults to `lint-baseline.xml`.
- **Fix**: `ImplicitSamInstance` lint not being enabled.

0.5.7
-----

_2023-02-15_

- **Fix**: `MergeFileTask.kt` was accidentally removed during a previous release.
- **Fix**: Add `jna-platform` dependency to align with the `jna` dependency version.

0.5.6
-----

_2023-02-15_

Do not use! Release was accidentally messed up.

0.5.5
-----

_2023-02-13_

- **Fix**: `LocTask` is now compatible with Gradle 8.0 and has the correct task dependencies when Ksp, Kapt, etc are running.
- **Fix**: `LocTask` is now compatible with remote build cache.
- **Enhancement**: ModScore now supports KSP.
- **Enhancement**: Binary download tasks (`KtfmtDownloadTask`, `DetektDownloadTask`, etc) now have prettier and more reliable download progress indications.
- **Enhancement**: `UpdateRobolectricJarsTask` now uses Gradle workers to parallelize downloads. On gigabit wifi, this takes the task runtime down from ~21sec to ~13sec.
- **Enhancement**: The boolean `SLACK_FORCE_REDOWNLOAD_ROBOLECTRIC_JARS` env variable can be used to force `UpdateRobolectricJarsTask` to redownload jars even if already downloaded.
**Behavior change**: Mod score must now be opted in to via the `slack.gradle.config.modscore.enabled=true` gradle property.
- **Enhancement**: Mod score can be disabled per-project via the `slack.gradle.config.modscore.ignore=true` gradle property.

0.5.4
-----

_2023-02-07_

* **New**: Integrate [gradle-dependency-sorter](https://github.com/square/gradle-dependencies-sorter) as another formatter. This automatically apply if the `sortDependencies` toml version is present, and you can have it download+create executable binaries via `./gradlew downloadDependenciesSorter`.
* **Enhancement**: Improve compose multiplatform support. Now the `compose()` DSL is moved to `slack.features` and offers an optional `multiplatform` parameter to enable the compose multiplatform plugin.
  ```kotlin
  slack {
    features {
      compose(multiplatform = <true|false>)
    }
  }
  ```
* Build against Kotlin `1.8.10` and AGP `7.4.1`.

0.5.3
-----

_2023-01-27_

* Fix the `MergeFilesTask` monkeypatch using env vars instead of system props.

0.5.2
-----

_2023-01-26_

* Try another fix for the `MergeFilesTask` monkeypatch plus extra logging. Feel free to skip this update if you're unaffected.

0.5.1
-----

_2023-01-23_

* **Fix**: Unwrap `Optional` for `google-coreLibraryDesugaring` dependency before adding it. The Gradle API's lack of type safety strikes again.

0.5.0
-----

_2023-01-23_

* **New**: Introduce a new `sgp-monkeypatch-agp` artifact. This contains monkeypatches for AGP where we try to fix bugs. This initial version contains a patched `MergeFilesTask` that sorts files before merging them to ensure deterministic outputs, as we believe this is causing our lint tasks to be non-cacheable across machines. This can be enabled via setting the `com.slack.sgp.sort-merge-files` system property to `true`.
* **New**: Rework how bugsnag is enabled. Instead of only applying the plugin in release/main builds, we now always apply the plugin and only enable uploads on release/main builds. This allows us to catch issues with the plugin in updates sooner, as before we would not see them on PRs.
  * Uploads can be force-enabled via setting the `slack.gradle.config.bugsnag.enabled` gradle property to true.
  * Branches that allow uploads can be configured via regexp value on the `slack.gradle.config.bugsnag.enabledBranchPattern` gradle property. For example: `slack.gradle.config.bugsnag.enabledBranchPattern=main|release_.*`.
* **New**: Source desugar libraries from `libs.versions.toml` instead of assuming the artifact name. Starting with 1.2.0, desugar JDK libs offers multiple artifacts. Point `google-coreLibraryDesugaring` in [libraries] to whichever artifact should be used.
* **Fix**: Catch and print errors with thermal closes.
* **Misc**: Update to JDK 19 and latest AGPs. The plugin itself still targets JVM 11 bytecode. AGP 7.4.0 is now required.

0.4.2
-----

_2023-01-13_

* **Enhancement:** Change default gradle memory percent in bootstrap from 25% to 50%.
* **Fix:** Support the gradle enterprise plugin retry implementation when using Gradle enterprise 3.12+.

0.4.1
-----

_2023-01-09_

Happy new year!

- **Fix**: Remove EitherNet compiler option opt-ins.
- Update Okio to `3.3.0`.

0.4.0
-----

_2022-12-29_

* Update to Kotlin 1.8.0. This plugin now requires Kotlin 1.8.0 or later as it only configures KGP's new `compilerOptions` API now.

0.3.8
-----

_2022-12-22_

* Add support for AGP 8.0.0-alpha10.

0.3.7
-----

_2022-12-19_

* **Fix:** Don't apply freeCompilerArguments.
* **Fix:** Add missing license information to published POM files.

0.3.6
-----

_2022-12-15_

* Backport `android.packagingOptions.jniLibs.pickFirst` for AGP 8.x compatibility, as the returned type by `jniLibs` changed from `JniLibsPackagingOptions` to `JniLibsPackaging`.

0.3.5
-----

_2022-12-06_

* Introduce `compileCiUnitTest` lifecycle task to just compile (but not run!) unit tests that are run by `ciUnitTest`.

0.3.4
-----

_2022-12-04_

* Only enable `isIncludeAndroidResources` in Android unit tests automatically when `robolectric()` is used.

0.3.3
-----

_2022-11-11_

* Add some more Kotlin compiler arguments to compilations. See [#160](https://github.com/slackhq/slack-gradle-plugin/pull/160)

0.3.2
-----

_2022-11-10_

* (Strict mode only) Check for `AndroidManifest.xml` files in androidTest sources + ensure they're debuggable.

0.3.1
-----

_2022-10-20_

* Exclude `**/build/**` from `Detekt` tasks.

0.3.0
-----

_2022-10-14_

* **New**: Support `com.android.test` projects.
* **New**: Preliminary support for AGP 8.x.
* Automatically add compose compiler dep in Compose multiplatform (i.e. `org.jetbrains.compose`) projects.
* Support Error Prone Gradle Plugin 3.x.
* Update `me.tongfei:progressbar` to `0.9.5`.

0.2.4
-----

_2022-10-06_

* **Fix**: Only check allowed androidTest variants if any are defined.

0.2.3
-----

_2022-10-03_

- **Fix:** Only configure bootstrap conditionally.

0.2.2
-----

_2022-10-03_

- Add necessary `--add-opens` to `Test` tasks for Robolectric 4.9+ when it's enabled.
- Avoid `subprojects` module stats and `allprojects` in bootstrap for better project isolation support.

0.2.1
-----

_2022-09-27_

- **Fix:** New `androidTest(allowedVariants = ...)` wasn't running on `com.android.application` projects.
- **Fix:** Configure `Lint` DSL block for `com.android.library` and `org.jetbrains.kotlin.jvm` projects too.

0.2.0
-----

_2022-09-23_

- Add option to enable only certain variants' android tests.

```kotlin
slack {
  android {
    features {
      androidTest(allowedVariants = setOf("internalDebug"))
    }
  }
}
```

0.1.2
-----

_2022-09-20_

- Support Robolectric jars for Android API 30.

0.1.1
-----

_2022-09-08_

- Fix wrong `slack.unit-test` plugin application.

0.1.0
-----

_2022-09-07_

- Update to Moshi 1.14.0.
- Disable `Instantiatable` lint in min SDK 28+ due to lint bug.
- Specify kotlin version in compose compatibility check.
