Changelog
=========

**Unreleased**
--------------

0.22.1
------

_2024-11-12_

- [gradle] Fix FoundryProperties hierarchical init.

0.22.0
------

_2024-11-12_

- [gradle] Introduce new `moduleTopography` task. This is a general task that attempts to validate project configurations by looking for features that are enabled but unused. Currently covers a few core features like kapt, ksp, moshi code gen, dagger/anvil, and viewbinding. Outputs are printed and written to a JSON file. Add `--validate-all` to make the tasks fail on validation issues.
- [gradle] Cache global `FoundryProperties` instance for subproject use.
- [gradle] Add `composeRuntimeOnly()` DSL feature.
- [cli] Check for non-existent paths before traversing in `Path.walkEachFile()`.
- [gradle] Don't depend on build sources in mod score if `MODULE_SCORE_INCLUDE_GENERATED` is false
- [gradle] Source module topography in mod score stats collecting
- [gradle] Build against Gradle `8.11`.
- [gradle] Do not track state in `RakeDependencies` task.
- Update to KotlinPoet `2.0.0`.
- Update to xmlutil `0.90.3`.
- Update to kaml `0.62.2`.
- Update to Mordant `3.0.1`.
- Build against gradle-dependency-sorter `0.12`.
- Build against KSP `2.0.21-1.0.27`.
- Build against DAGP `2.4.2`.
- Build against Compose Multiplatform `1.7.0`.

0.21.0
------

_2024-10-25_

- **New**: Extract `better-gradle-properties` artifact, which is our hierarchical Gradle properties solution that better handles properties in Gradle. This is what powers `FoundryProperties` but is now extracted to be more portable.
  - This checks in the following order of priority
    - project-local `local.properties`
    - project-local `gradle.properties`
    - root-project `local.properties`
    - root-project/global `gradle.properties`
- **New**: Extract `version-number` artifact. This contains a Kotlin, non-deprecated `VersionNumber` API. This is useful given that Gradle's is both deprecated and not available in standard JVM projects.
- **New**: Support Robolectric 4.14.
- **Changed**: Remove defunct `foundry.git.hooksPath` and `foundry.git.ignoreRevsFile` properties.
- **Changed**: Rename `SgpArtifact` to `FoundryArtifact`.
- Misc doc fixes + reorganize docsite around new repo structure.
- Update xmlutil to `0.90.2`.
- Build against Spotless `7.0.0.BETA4`.
- Build against KSP `2.0.21-1.0.26`.
- Build against DAGP `2.2.0`.
- Build against nullaway-gradle `2.1.0`.
- Build against errorprone-gradle `4.1.0`.
- Build against Compose multiplatform `1.7.0`.
- Build against Dokka `2.0.0-Beta`.

0.20.2
------

_2024-10-14_

- **Fix**: Fall back to old property name for kotlin JVM args if the new one isn't found.
- **Enhancement**: Don't try to apply kotlin JVM args if there are none.

0.20.1
------

_2024-10-14_

Don't use this release! We goofed on something.

0.20.0
------

_2024-10-11_

### Project Restructuring

We've restructured this project! Since its early days as a simple Gradle convention plugin it's expanded into IntelliJ plugins, CLIs, CI tooling, and more. To better capture this, we've renamed the project to *Foundry*, restructured its artifacts into a more cohesive setup, moved kotlin-cli-util into this repo, and will likely split out some more artifacts down the line.

#### Migration Guide

- Any **gradle properties** prefixed with `slack.` or `sgp.` have now moved to `foundry.`. `slack.gradle.` prefix have also removed the `gradle.` (i.e. `slack.gradle.foo` would now be `foundry.foo`).
  - `SlackProperties` has been renamed to `FoundryProperties`.
  - Some properties have been further refined to be more specific.
  - There is a helper script you can use to automatically rename all properties in `foundry-migration/`.
    - Download `mapping.txt`
    - Download `property_migration.sh`
    - Run `./property_migration.sh mapping.txt <path to target project>`.
      - Note this only covers `*.kts` and `*.properties` files. This does not cover args passed via CLI or envs.

- Package names have all aligned to `foundry.*`. In most cases it should be simple enough to replace `import com.slack.*` with `import foundry.*`.
- Gradle coordinates group have moved to the `com.slack.foundry` group name.

    | Old Artifact ID     | New Artifact ID |
    |---------------------|-----------------|
    | sgp                 | gradle-plugin   |
    | sgp-agp-handler-api | agp-handler-api |
    | sgp-common          | foundry-common  |
    | sgp-tracing         | tracing         |
    | skippy              | skippy          |

- The former `kotlin-cli-util` APIs have moved to the `com.slack.foundry:cli` artifact.
- Platform-specific plugins now live under the `platforms/` directory.
- All other tools now live under the `tools/` directory.
- Most top-level `Slack`-prefixed APIs are now prefixed with `Foundry`.
- The primary gradle entry points are now `foundry {}`, but the previous `slack {}` entry points are left with deprecation `ReplaceWith` options.
- Gradle plugin IDs have migrated to `com.slack.foundry.*` names.

    | Old                               | New                                |
    |-----------------------------------|------------------------------------|
    | `com.slack.gradle.root`           | `com.slack.foundry.root`           |
    | `com.slack.gradle.base`           | `com.slack.foundry.base`           |
    | `com.slack.gradle.apk-versioning` | `com.slack.foundry.apk-versioning` |

- Introduce new required `foundry.android.defaultNamespacePrefix` property for android projects. This is necessary for projects that don't define an `android.namespace` explicitly.

### Misc

- **New**: Make JDK configuration docs and error messages configurable via `FoundryProperties`.
- **Fix**: Gracefully handle undefined kapt language versions when computing `progressive`.
- **Fix**: Track deleted build files when computing parent projects in Skippy.
- Update Clikt to `5.0.1`.
- Update oshi-core to `6.6.5`.
- Build against DAGP 2.x.
- Build against gradle-error-prone `4.0.1`.
- Build against gradle-nullaway `2.0.0`.
- Build against Gradle versions plugin `0.51.0`.
- Build against Develocity plugin `3.18.1`.

0.19.6
------

_2024-09-25_

- Intro `SoftRefLRUPolicyMSPerMB` + `ReservedCodeCacheSize` jvm args to bootstrap
  - Learnings from https://jasonpearson.dev/codecache-in-jvm-builds and https://jasonpearson.dev/softreflrupolicymspermb-in-jvm-builds.
- Update kotlin-cli-util to `3.0.1`.
- Update to Guava `33.3.1-jre`.
- Build against Gradle `8.10.2`.

0.19.5
------

_2024-09-23_

- Add workaround for KGP not applying `sourceInformation` compose options in android projects and default it to true.

0.19.4
------

_2024-09-22_

- Change `slack.allowWarnings` property to `sgp.kotlin.allowWarnings`.
- Disallow warnings in Kotlin test compilations by default, add `sgp.kotlin.allowWarningsInTests` property to opt-out.
- Update to kotlinx-coroutines `1.9.0`.
- Update Okio to `3.9.1`.
- Update to Clikt `5.0.0`.
- Update JNA to `5.15.0`.
- Update oshi-core to `6.6.4`.
- Update kotlin-cli-util to `3.0.0`.
- Build against AGP `8.6.1`.

0.19.3
------

_2024-09-12_

- **Change**: Rename `testFixtures()` feature DSL to `enableTestFixtures()` to disambiguate from Gradle's methods.
- **New**: Introduce new `sgp.compose.stabilityConfigurationPath` property to specify a Compose configuration file path from the root project. It is also now disallowed to specify this via `sgp.compose.commonCompilerOptions`.

0.19.2
------

_2024-09-12_

- [sgp] **New**: Add new `slack { features { testFixtures() } }` DSL for enabling test fixtures in a project-agnostic way.
- [sgp] Make `slack.location.robolectric-core` property optional.
- [sgp] Clean up duplicate Kotlin JVM argument handling in `KaptGenerateStubsTask`.
- [sgp] Update to the new compose-compiler-gradle-plugin feature flag API.
- [sgp] Apply platform project BOMs to SqlDelight `DialectClasspath` configurations too.
- [sgp] Build against KSP `2.0.20-1.0.25`.
- [sgp] Build against AGP `8.6.0`.
- [sgp] Build against Gradle `8.10.1`.
- [sgp] Build against Wire `5.1.0`.
- [skippy] Don't treat testFixtures as test sources

0.19.1
------

_2024-09-03_

- Add `slack.bootstrap.minGradleXms` property to set minimum xms values for daemon memory args.
- Apply `slack.boostrap.minGradleXm*` values to kotlin daemon jvm args too.
- Remove MaxMetaspace caps from bootstrap. This shouldn't be set anymore in JDK 17+.
- Update develocity-gradle-plugin-adapters to `1.0.4`.
- Update Bugsnag to `3.7.2`.
- Build against AGP `8.6.0`.

0.19.0
------

_2024-08-27_

- Improve KGP plugin handling to better support upcoming AGP kotlin compilation.
- Build with JDK 22. Still targets JVM 17.
- Update MoshiX to `0.28.0`.
- Update RxJava to `3.1.9`.
- Update Oshi to `6.6.3`.
- Revert dependency sorter back to `0.7`.
- Build against Kotlin `2.0.20`.
- Build against redacted-compiler-plugin `1.10.0`.
- Build against KSP `2.0.20-1.0.24`.
- Build against Gradle `8.10`.

0.18.4
------

_2024-08-12_

- Add `sgp.kapt.languageVersion` property to override the language version used for KAPT.
- Build against Kotlin `2.0.10`.
- Build against KSP `2.0.10-1.0.24`.
- Build against AGP `8.5.2`.

0.18.3
------

_2024-08-04_

- Add support for changing Anvil implementations.

0.18.2
------

_2024-07-31_

- Fix applying free compiler args from Gradle properties.
- Gate disabling compose live literals on relevant AGP versions. This is deprecated in new AGP versions.
- Remove a noisy log.
- Streamline dagger ksp handling + remove napt support.
- Update Kaml to `0.61.0`.
- Update Oshi to `6.6.2`.
- Build against DAGP `1.33.0`.
- Build against KSP `2.0.0-1.0.24`.
- Build against Anvil `2.5.0-beta11`.

0.18.1
------

_2024-07-19_

- **Fix**: Fix circular evaluation dependency for `jvmTarget`.
- Remove Kover Workaround for `0.7.1`.
- Turn off xml report generation for lint task.
- Update KotlinPoet to `1.18.1`.
- Update Wire to `5.0.0`.
- Update MoshiX to `0.27.2`.
- Build against Gradle `8.9`.
- Build against KSP `2.0.0-1.0.23`.
- Build against AGP `8.5.1`.
- Build against sort-dependencies `0.7`.

0.17.2
------

_2024-05-28_

- Make default Kotlin compiler arguments more configurable. This includes removing some previously-default behavior.
  - `sgp.kotlin.languageVersionOverride`. Default is null, which will just defer to KGP's default.
  - `sgp.kotlin.optIns`
    - Previous default opt-ins = `kotlin.contracts.ExperimentalContracts`, `kotlin.experimental.ExperimentalTypeInference`, `kotlin.ExperimentalStdlibApi`, `kotlin.time.ExperimentalTime`
  - `sgp.kotlin.freeArgs`
    - Previous default free args = `-Xenhance-type-parameter-types-to-def-not-null`, `-Xself-upper-bound-inference`
  - `sgp.kotlin.jvmFreeArgs`
    - Previous default JVM free args = `-Xjsr305=strict`, `-Xassertions=jvm`, `-Xemit-jvm-type-annotations`, `-Xjvm-default=all`, `-Xtype-enhancement-improvements-strict-mode`, `-Xjspecify-annotations=strict`
  - `sgp.kotlin.progressive`. Default is true.
- Build against AGP `8.5.0`.
- Build against DAGP `1.32.0`.
- Build against Compose Multiplatform `1.6.11`.
- Update to Kaml `0.60.0`.
- Update to Guava `33.2.1-jre`.
- Update to MoshiX `0.27.1`.

0.17.1
------

_2024-05-28_

- Build against Kotlin `2.0.0`.
- Build against KSP `2.0.0-1.0.21`.
- Build against AGP `8.4.1`.

0.17.0
------

_2024-04-30_

- Build against AGP `8.4.0`.
- Update Clikt to `4.4.0`.

0.16.4
------

_2024-04-26_

- Add new flag + logic to relocate project build directories [#827](https://github.com/slackhq/slack-gradle-plugin/pull/827).
- Update to develocity plugin's `retry` extension.
- Build against redacted compiler plugin `1.8.1`.
- Update to Kaml `0.59.0`.

0.16.3
------

_2024-04-18_

- Migrate to Develocity (3.17) for build scan reporting. Technically legacy Gradle Enterprise APIs should still work, but we're not intentionally supporting them anymore.
- Build against Anvil `2.5.0-beta07`.
- Update commons-text to `1.12.0`.
- Update oshi to `6.6.0`.
- Build against DAGP `1.31.0`.
- Build against Compose Multiplatform `1.6.2`.
- Build against AGP `8.3.2`.
- Build against KSP `1.9.23-1.0.20`.
- Build against SqlDelight `2.0.2`.
- Update to Wire `4.9.9`.
- Update to Clikt `4.3.0`.

0.16.2
------

_2024-03-28_

- Work around https://issuetracker.google.com/issues/331806519 when using KSP in android projects.
- Build against AGP `8.3.1`.
- Build against Detekt `1.23.6`.
- Build against Gradle `8.7`.
- Update Retrofit to `2.11.0`.
- Update Kaml to `0.58.0`.
- Update Wire to `4.9.8`.
- Update Guava to `33.1.0-jre`.

0.16.1
------

_2024-03-14_

- Only apply `-Xjdk-release` on jvm compilations (not android).

0.16.0
------

_2024-03-14_

- **New**: Add support for Anvil's new KSP support via Anvil `2.5.0-beta04`.
- Incorporate Kotlin's `-Xjdk-release` option alongside `jvmTarget`.
- Fix configuration cache when setting kotlin compilation module name.
- Update Okio to `3.9.0`.
- Update Oshi to `6.5.0`.
- Build against Kotlin `1.9.23`.
- Build against KSP `1.9.23-1.0.19`.
- Build against Compose Multiplatform `1.6.1`.

0.15.9
------

_2024-03-08_

- **New**: Add property to override lint version from version catalogs.
- **Fix**: Fix application of common compose options.

0.15.8
------

_2024-03-04_

- **Fix**: Check for `com.android.test` in `Project.isAndroid` checks. This resolves a case where we accidentally set the `JavaCompile.options.release` property in those projects.

0.15.7
------

_2024-02-28_

- Fix inverted logic for disabled anvil mode in K2/KSP testing.

0.15.6
------

_2024-02-28_

- Fix `AnvilMode` casing in detection.

0.15.5
------

_2024-02-27_

- Broaden anvil KSP support controls.
- Update oshi to `6.4.13`.
- Add `sgp.compose.commonCompilerOptions` property for common compose compiler options.
- Build against Compose Multiplatform `1.6.0`.
- Fix mkdocs lists on the main page.

0.15.4
------

_2024-02-19_

- Remove `-Xproper-ieee754-comparisons` arg, as it's enabled by default and removed entirely in K2.
- Small fixes around skippy + add inverse options to boolean options.
- Update Okio to `3.8.0`.
- Update Oshi to `6.4.12`.
- Update coroutines to `1.8.0`.
- Build against DAGP `1.30.0`.
- Build against AGP `8.3.0-rc01`.

0.15.3
------

_2024-02-03_

- Support Gradle 8.6.
- Use new lazier `TaskCollection.named(Spec)` APIs where appropriate.
- Update to Moshi `1.15.1`.
- Update to kotlin-cli-util `2.6.3`.
- Update to oshi `6.4.11`.
- Build against AGP `8.2.2`.
- Build against Kotlin `1.9.22`.
- Build against KSP `1.9.22-1.0.17`.
- Build against Detekt `1.23.5`.
- Build against Compose Multiplatform `1.5.12`.
- Build against DAGP `1.29.0`.
- Build against Spotless `6.25.0`.

0.15.2
------

_2024-01-11_

- **New**: Promote `PropertyResolver` to public API.
- **New**: Skippy logic is now distributed as a separate, non-gradle-specific artifact under `com.slack.gradle:skippy`. This also includes a CLI that can be run as an alternative to the gradle task. Note this requires serialized dependency graph and androidTest projects to be pre-computed. THe config must be in a static JSON file. See the docs on `ComputeAffectedProjectsCli`.

  ```
  Usage: compute-affected-projects-cli [<options>]

    Computes affected projects and writes output files to an output directory.

  Options:
    --debug                    Enable debug logging.
    --merge-outputs            Merge outputs from all configs into a single
                               /merged dir.
    --config=<path>            Path to a config file that contains a mapping of
                               tool names to SkippyConfig objects.
    --parallel                 Compute affected projects in parallel.
    --changed-files=<path>     A relative (to the repo root) path to a
                               changed_files.txt that contains a
                               newline-delimited list of changed files. This is
                               usually computed from a GitHub PR's changed files.
    -o, --outputs-dir=<path>   Output directory for skippy outputs.
    --root-dir=<path>          Root repo directory. Used to compute relative
                               paths.
    --dependency-graph=<path>  Path to a serialized dependency graph file.
    --android-test-project-paths=<path>
                               Path to a file that contains a newline-delimited
                               list of project paths that produce androidTest
                               APKs.
    -h, --help                 Show this message and exit
  ```
- **Enhancement**: Split out separate cacheable `generateDependencyGraph` and `generateAndroidTestProjects` tasks from `ComputeAffectedProjectsTask`. This allows for more fine-grained caching and parallelization.

An example flow of the last two bullets can look like so:

```bash
# Generate the dependency graph and androidTest projects
./gradlew generateDependencyGraph generateAndroidTestProjectPaths

# Run the CLI, such as from a `*.main.kts` file that imports it.
./skippy-runner.main.kts \
    --changed-files changed_files.txt \
    --dependency-graph slack/dependencyGraph/serializedGraph.bin \
    --android-test-project-paths slack/androidTestProjectPaths/paths.txt \
    ...
```

0.15.1
------

_2024-01-09_

- Misc small fixes to the new artifact publishing internals released in 0.15.0.

0.15.0
------

_2024-01-02_

- **Enhancement**: We've reworked the internals of SGP significantly to make it more compatible with Gradle Project Isolation. This affects Skippy, ModScore, AndroidTest APK aggregation, DAGP missing identifiers, and property resolution (specifically command line properties and `local.properties`). Note that this work is ongoing as project isolation is incubating, but the changes in this release should be non-functional in nature.
- Build against Kotlin `1.9.22`.
- Build against KSP `1.9.22-1.0.16`.
- Build against AGP `8.2.1`.
- Build against AGP `8.3.0-beta01` in AgpHandler83.
- Build against maven-publish `0.26.0`.
- Update to Guava `33.0.0-jre`.
- Update to Oshi `6.4.10`.
- Update to kotlin-cli-util `2.6.0`.

0.14.2
------

_2023-12-18_

- Make `compose()` DSL function with Action parameter public.

0.14.1
------

_2023-12-18_

- Expose `compilerOption()` API in Compose DSL for compiler arg configuration.
- Update to DAGP `1.28.0` + migrate off deprecated APIs.
- Update Okio to `3.7.0`.
- Update AgpHandler 8.3 to `8.3.0-alpha18`.
- Build against KSP `1.9.21-1.0.16`.

0.14.0
------

_2023-12-12_

- Support granular Skippy configuration. Now each tool can be configured independently with both global and per-tool configuration. These are controlled via public `skippy` extension now. These outputs and diagnostics are stored at `build/skippy/{tool}/...`. Merged outputs can be generated as well to `build/skippy/merged`. This allows for creating dynamic pipelines based on the outputs of each tool. The global config is always overlaid onto each tool-specific config.

  ```kotlin
  skippy {
    debug.set(true)
    mergeOutputs.set(true)
    computeInParallel.set(true)
    global {
      applyDefaults()
      // Glob patterns of files to include in computing
      includePatterns.addAll(
        "**/*.pro",
        "**/src/**/sqldelight/**",
      )
      excludePatterns.addAll(".idea/**/*.kt")
      // Glob patterns of files that, if changed, should result in not skipping anything in the build
      neverSkipPatterns.addAll(
        ".buildkite/**",
        ".github/actions/**",
        "ci/**",
        "config/health-score/**",
        "tooling/scripts/**",
      )
    }
    config("lint") {
      includePatterns.addAll(
        // project-local lint.xml files
        // this doesn't fuuuuully work with skippy because these layer like .gitignore does
        "**/lint.xml",
        // Lint baselines
        "**/lint-baseline.xml",
      )
      neverSkipPatterns.addAll(
        // Global lint config
        "config/lint/lint.xml",
        // Houston feature flags, which is an input to our feature flags lints
        "config/feature-flags/experiments.txt",
      )
    }
    config("detekt") {
      // Detekt baselines
      includePatterns.add("**/detekt-baseline.xml")
      // Global detekt configs
      neverSkipPatterns.add("config/detekt/*")
    }
  }
  ```

- Update Kotlin language version to `1.9`.
- Upgrade away from deprecated CC API check.
- Update to okio `3.6.0`
- Update to oshi `6.4.9`
- Update to JNA `5.14.0`
- Update to kotlin-cli-util `2.5.4`
- Build against AGP `8.3.0-alpha17` in AgpHandler 8.3 artifact.
- Build against DAGP `1.27.0`.

0.13.1
------

_2023-12-05_

- Make `Project.isSyncing` public.
- Update to new `android.studio.version` property for reporting to build scans.
- Update to Wire `4.9.3`.
- Update to RxJava `3.1.8`.
- Build against Kotlin `1.9.21`.
- Build against KSP `1.9.21-1.0.15`.
- Build against MoshiX `0.25.1`.
- Build against AGP `8.2.0`.
- Build against SqlDelight `2.0.1`.
- Build against Redacted Compiler Plugin `1.7.1`.
- Build against Detekt `1.23.4`.
- Build against Gradle Doctor `0.9.1`.

0.13.0
------

_2023-11-30_

- Update to Gradle 8.5. This version requires Gradle 8.5+.
- Report Gradle 8.5's new `BuildFeatures` to build scans as custom values, starting with configuration cache and isolated projects.
- Update Guava to `32.1.3-jre`.
- Update Oshi to `6.4.8`.
- Build against AGP `8.1.4` (main) and `8.3.0-alpha15` (agp handler 8.3).
- Build against compose multipaltform `1.5.11`.
- Include source links in Dokka docs.

0.12.1
------

_2023-11-21_

- Fix circuit() extension code gen using the wrong configuration.

0.12.0
------

_2023-11-20_

- **New**: Add a `SlackExtension.circuit()` DSL. This makes it easy to set up [Circuit](https://github.com/slackhq/circuit) in a project. See the DSL docs for more details.
  ```kotlin
  slack {
    features {
      circuit()
    }
  }
  ```
- Don't configure `KspTask` subtypes of `KotlinCompile` tasks.
- Remove `autoValue()` APIs from `SlackExtension`.
- Fix deprecated forkEvery call.

0.11.7
------

_2023-11-10_

- Make AGP version resolution lazy in AgpHandlers.

0.11.6
------

_2023-11-09_

- Explicitly set `Detekt.baseline` to null if we don't specify one, as we don't want it to fall back to its default location.

0.11.5
------

_2023-11-09_

- Support AGP `8.3.0-alpha13`.

0.11.4
------

_2023-11-08_

- Detekt baselines are now generated into their corresponding projects. This matches how we handle lint baselines, and global baselines are no longer supported.
- You do now need to specify a `slack.detekt.baseline-file-name` property to indicate what the simple file name should be. This is evaluated against `project.layout.projectDirectory.file(...)`. This replaces the previous `slack.detekt.baseline` property.
- Build against Kotlin `1.9.20`.
- Build against AGP `8.1.3`.
- Build against Compose Multiplatform `1.5.10`.

0.11.3
------

_2023-11-02_

- Support Robolectric 4.11 jars for Android API 34.

0.11.2
------

_2023-10-30_

- Make detekt and lint baselines default to `null` if their property values are blank.
- No longer publish monkeypatch artifact as it's no longer necessary.

0.11.1
------

_2023-10-22_

- Add new `sgp.isTestLibrary` property to indicate if a library is a test library. Note that projects that are named `test-fixtures` are implicitly considered test libraries.
- **Fix**: Don't impose `VisibleForTests` lint on test libraries.

0.11.0
------

_2023-10-22_

- Revamp some lint configuration and best-effort support KMP projects.
- Remove `slack.lint.update-baselines` property in favor of AGP's modern `updateBaselines` task.
- Remove hidden `ImplicitSamInstance` lint config.
- Disable newer issues for dependency checks.

0.10.11
-------

_2023-10-19_

- Support AGP `8.3.0-alpha08`.
- Build against Gradle `8.4`.
- Build against compose-multiplatform `1.5.3`.

0.10.10
-------

_2023-10-02_

- Make debug builds everywhere (local and CI) use consistent version codes and version names. This way remote build cache entries for them are compatible.
  - Default debug version code is governed by `slack.gradle.debugVersionCode` and defaults to `90009999`.
  - Default version name user suffix is governed by `slack.gradle.debugUserString` and defaults to `debug`.
  - Non-debug build types are the same as before.
- Remove a noisy lifecycle log around lint variant selection.
- Build against Bugsnag Gradle Plugin `8.1.0`.

0.10.9
------

_2023-09-28_

- Don't disable caching on MergeAssets tasks by default. Leave that to consuming repos.
- Update permission allowlist API to use a `allowListFile` file property instead.
- Don't exclude coroutines' debug probes in debug build packaging.
- Support AGP 8.3.0-alpha05+.
- Build against Wire `4.9.1`.
- Build against Compose Multiplatform `1.5.2`.
- Update to Oshi `6.4.6`.

0.10.8
------

_2023-09-22_

- **Fix**: Don't apply boms to the `coreLibraryDesugaring` configuration.
- **Enhancement**: Set Kotlin `compilerOptions.moduleName` to a dashified version of the Gradle project path.
- Update MoshiX to `0.24.3`
- Compile against Anvil `2.4.8`.
- Compile against CM `1.5.1`.
- Compile against DAGP `1.22.0`.
- Compile against redacted-compiler-plugin `1.6.1`.

0.10.7
------

_2023-08-29_

- **Fix**: Don't apply BOMs to testApi configurations.

0.10.6
------

_2023-08-29_

- **New**: Publish a generic Tracing API under the `sgp-tracing` artifact ID. We use this internally to collect extra build metadata, but it's generic enough for general use. Not currently used in slack-plugin yet.
- Add `sgp.android.buildToolsVersionOverride` property to override the default build tools version in Android.
- Migrate to `AndroidPluginVersion` API in `AgpHandler`.
- Fix a few more deprecated Gradle API usages.
- Build against AGP `8.1.1`.
- Build against Spotless `6.21.0`.
- Build against Compose Multiplatform `1.5.0`.
- Update to Kotlin `1.9.10`.
- Update to KSP `1.9.10-1.0.13`.
- Update to RxJava `3.1.7`.
- Update to kotlin-cli-util `2.2.1`.
- Update to Oshi `6.4.5`.
- Update to Wire `4.8.1`
- Update to DAGP `1.21.0`.
- Update to Gradle `8.3`.

0.10.5
------

_2023-08-15_

- **Fix**: Better support `com.android.test` projects in a few ways
  - No longer apply lint configurations as they don't support them.
  - No longer apply DAGP as it doesn't support them yet.
  - No longer apply unit test configurations as they don't have unit tests.
- Build against Gradle 8.3 + fix a few deprecated `Project.buildDir` API usages.
- Update `kotlin-cli-util` to `2.1.0`.

0.10.4
------

_2023-08-10_

- **New**: New DSL API for enabling Android resources.

This streamlines configuration of enabling `androidResources` and enforces use of a resource prefix to avoid conflicts.

May your avatars never be wrongly sized again.

```kotlin
slack {
  android {
    features {
      resources("prefix_")
    }
  }
}
```

- Update kotlin-cli-util to `2.0.0`.
- Update Moshi to `1.15.0`.
- Update sort-dependencies to `0.4`.
- Update KSP to `1.9.0-1.0.13`.
- Update Okio to `3.5.0`.
- Update Jetbrains Markdown to `0.5.0`.

0.10.3
------

_2023-08-08_

- Fix an issue with Skippy where we would accidentally mark all library projects as affected androidTest projects even if they didn't have androidTest enabled.
- Update Guava to `32.1.2-jre`.

0.10.2
------

_2023-08-08_

- Apparently the bugsnag plugin relies on the version code to be set in the variant output, so now we set a default again (configurable via `slack.gradle.defaultVersionCode`). The default is `90009999`, for _reasons_.

0.10.1
------

_2023-08-07_

- Use a single version code for all APK architectures.
  - Before, we used to compute a different version code for each architecture. This is kinda silly, and broke with AGP 8.1.0. So now we no longer do this. This means that by default, no custom version code is set unless an override is set in `{rootProject}/build/ci/release.version`. The default behavior will be to just use the version set in the `android` DSL.
- Update to Detekt `1.23.1`.
- Update to Gradle Enterprise `3.14.1`.
- Update to Compose Multiplatform `1.4.3`.
- Update to SqlDelight `2.0.0`.
- Update to Anvil `2.4.7`.

0.10.0
------

_2023-07-25_

- Update to Kotlin `1.9.0`.
- Update to KSP `1.9.0-1.0.12`.
- Update to AGP `8.1.0`.
- Update kotlin-cli-util to `1.2.3`.
- Update MoshiX to `0.24.0`.
- Update redacted to `1.5.0`.
- Update wire to `4.8.0`.
- Add new dagger flag to ignore wildcard keys by default.
  - https://dagger.dev/dev-guide/compiler-options#ignore-provision-key-wildcards

0.9.18
------

_2023-07-12_

- Fix: Differentiate unit and instrumentation tests in Skippy. Now changing a unit test will not cause the instrumentation tests to be marked as affected.
- Add `slack.dependencyrake.dryRun` gradle property flag for dependency rake to enable dry-run. If enabled, the project build files will not be modified and a separate `new-build.gradle.kts` file will be written to instead.
- Update Okio to `3.4.0`.

0.9.17
------

_2023-07-07_

- Don't register `RakeDependencies` task on platform projects.
- Fix configuration cache for Dependency Rake. Note that DAGP doesn't yet support it.
- Add Dependency Rake usage to its doc.
- Add missing identifiers aggregation for Dependency Rake. This makes it easier to find and add missing identifiers to version catalogs that dependency rake expects.
  - `./gradlew aggregateMissingIdentifiers -Pslack.gradle.config.enableAnalysisPlugin=true --no-configuration-cache`

0.9.16
------

_2023-06-30_

- Enable lint on test sources by default.
- Account for all version catalogs in `DependencyRake`.
- Update Guava to `32.1.0-jre`.

0.9.15
------

_2023-06-29_

- Switch Robolectric jar downloads to use detached configurations.
  - This lets Gradle do the heavy lifting of caching the downloaded jars and also allows downloading them from a configured repository setup. This also simplifies the up-to-date checks.
- Docs are now published on https://slackhq.github.io/slack-gradle-plugin. This is a work in progress.
- API kdocs are published at https://slackhq.github.io/slack-gradle-plugin/api/0.x/.
- Update `kotlin-cli-util` to 1.2.2.

0.9.14
------

_2023-06-25_

* Fix compose compiler config not applying to android projects.

0.9.13
------

_2023-06-24_

* Fix wrong map key name being used in exclusion.

0.9.12
------

_2023-06-24_

* Fix wrong dependency being used for compose-compiler in new Compose configuration overhaul.

0.9.11
------

_2023-06-24_

* Overhaul configuration of the Compose compiler across Android and multiplatform projects to make it easier to test the matrix of the AndroidX compose compiler, the JetBrains compose compiler, and new Kotlin versions.
* **New**: Add a `sgp.compose.multiplatform.forceAndroidXComposeCompiler` Gradle property flag to force use of the AndroidX compose compiler in Compose Multiplatform projects.
* **New**: Add a `sgp.config.jvmVendor.optOut` Gradle property flag to disable jvmVendor configuration in toolchains.
* **Fix**: No longer try to watch thermals on Intel macOS machines when configuration cache is enabled.

0.9.10
------

_2023-06-06_

- **Fix**: Don't apply vendor property to Bootstrap toolchain if it's undefined.

0.9.9
-----

_2023-06-06_

- **New**: Robolectric is now optional. Its support will only be enabled if there is a `robolectric` key in the primary version catalog.
- **Enhancement**: Update bootstrap JVM args to use ExitOnOutOfMemoryError for OOMs. No longer takes a heap dump on OOM.
- **Enhancement**: Update unit test verbose mode to use CrashOnOutOfMemoryError for OOMs to produce more logs.
- **Fix**: Bootstrap now respects the `sgp.config.jvmVendor` property.
- Build against Wire to `4.7.0`.
- Build against Anvil `2.4.6`.
- Build against AGP `8.0.2`.
- Build against Detekt `1.23.0`.

0.9.8
-----

_2023-05-15_

- Fix eager check of whether or not androidTest is enabled in `slack` DSL.

0.9.7
-----

_2023-05-15_

- Fix application targets not getting marked as androidTest()-enabled.

0.9.6
-----

_2023-05-09_

- Fix wrong use of `disallowChanges()` on `javaCompiler` in JavaCompile tasks. It seems that Gradle sets this multiple times.
- Improve git version matching.

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
