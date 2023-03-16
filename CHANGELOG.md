Changelog
=========

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
