Architecture
============

Foundry contains three Gradle plugins and some associated helper artifacts.

## `FoundryRootPlugin`

This is the root plugin that is applied to the root project of a multi-project build.

```kotlin
plugins {
  id("foundry.root")
}
```

Its responsibilities include:

- Registering the `FoundryTools` build service.
- Setting up global configuration (i.e. global lifecycle tasks, download tasks, etc).
- Validating the JDK matches the expected JDK defined in `libs.versions.toml`.
- Configure git (hooks, ignored revisions, etc).
- (If running on macOS) Validating the build isn't running in Rosetta mode.

## `FoundryBasePlugin`

This is the base plugin that is applied to _all_ projects (including the root project).

```kotlin
plugins {
  id("foundry.base")
}
```

Its responsibilities include:

- Configuring formatters for Spotless.
- Configuring the buildscript and dependency classpaths. This includes "fixing" any known bad dependencies (Hamcrest and CheckerFramework are a mess), failing on non-reproducible dependency declarations.
- Running standard subproject configurations via `StandardProjectConfigurations`.
- Configuring unit tests via `UnitTests`. This also includes configuring the Gradle test retry plugin, if enabled.
- Configuring NullAway, if enabled.
- Configuring [Skippy](/tools/skippy).
- Configuring [Mod Score](../mod-score) tasks.

### `StandardProjectConfigurations`

This class warrants special mention as it is responsible for the bulk of the configuration applied to projects SGP manages.

- Creates and exposes the [`foundry` extension DSL](../dsl).
- Applies common configurations.
  - This largely just sets up the dependency sorter plugin.
- Applies common JVM configurations.
- Applies common Kotlin configurations.

#### JVM Configurations

All JVM projects (Android, Java, Kotlin) receive some common configuration for their JVM tasks.

##### Common

- Applies the repo's platform project, if any.
- Applies any BOM dependencies to platform-configurable configurations.
- Configures the dependency analysis gradle plugin and [`DependencyRake`](../dependency-rake).
- Applies common annotations and common test bundles from version catalogs.
- Fails on non-androidx support library dependencies.
- Configure common annotations processors.
  - This logic is largely in `StandardProjectConfigurations.configureAnnotationProcessors()` and `StandardProjectConfigurations.APT_OPTION_CONFIGS`, which seeks to apply common configs for known processors like Dagger and Moshi.

##### Java

Java projects are fairly simple. Note that these are applied on all projects that apply the `java` plugin, which is most of them!

- They are just configured to ensure their source/target compatibility is set to the repo's JVM target.
- In non-android projects, `JavaCompile` tasks have their `options.release` property set to this as well.
- Gradle toolchains are used to manage the JDK used for `JavaCompile` tasks in non-android projects to ensure consistency.
- All `JavaCompile` tasks have `-parameters` added to `options.compilerArgs` for better static analysis and annotation processing support.
- If opted-in, error-prone and nullaway are applied and set up with the project with common configurations (configured checks, ignoring build dirs, etc).
  - Foundry supports Error-Prone's auto-patching mode via enabling the `foundry.epAutoPatch` property.

##### Android

- Configures AndroidTest APK aggregation with [Skippy](/tools/skippy) support.
- Applies the [Android cache fix plugin](https://github.com/gradle/android-cache-fix-gradle-plugin), if enabled.
- Configures common AGP extensions (both legacy extensions and new Component extensions).
  - Disables unused/irrelevant variants. SGP is single-variant for library projects by default.
  - Disables android tests on projects unless opted in.
  - Disables unit tests on app projects by default (we use the app project as just a shell project for producing an APK).
  - Configures `compileOptions`, `defaultConfig`, compileSdk/targetSdk/minSdk/ndkVersion, etc.
    - Enables `vectorDrawables.useSupportLibrary`.
  - Enables core library desugaring.
  - Configures common `testOptions` like orchestrator, `unitTests`, etc.
    - `unitTests.isReturnDefaultValues` is always enabled for convenience.
    - `unitTests.isIncludeAndroidResources` is _only_ enabled if robolectric is enabled on the project, as this is expensive to enable.
    - Set up unit test `Test` tasks to depend on the `UpdateRobolectricJarsTask` if robolectric is enabled.
  - Patches objenesis dependency versions due to weird transitive dependencies in android test classpaths.
  - Configures `com.android.library` and `com.android.application` projects. `com.android.test` is supported but somewhat limited.
- Application projects...
  - have their packaging config set up with some convenience common exclusions and handling common `jniLibs` handling.
  - have v3 and v4 signing enabled by default.
  - are configured with [`PermissionChecks`](../utilities/#permissionchecks).
  - are configured with the Bugsnag gradle plugin, if enabled.
- Library projects...
  - are configured with an automatic `android.namespace`, if none is manually specified in the buildscript. The namespace is inferred from the project's Gradle path.
  - are single-variant by default, set to `release`.

#### Kotlin Configurations

Kotlin projects are configured with KGP and Detekt in mind. SGP supports configuring Android, JVM, Multiplatform, and Compose Multiplatform projects. Multiplatform for targets other than JVM/android is limited at the moment.

Common configurations include:

- Setting computed kotlin daemon JVM args.
- Setting the `jvmToolchain` to align with the repo's JDK target.
- Configuring `KotlinCompilation` tasks with common configurations.
  - Enabling `allWarningsAsErrors`.
  - Adding free compiler args to `freeCompilerArgs`.
    - If a JVM compilation, add extra JVM args + set `jvmTarget` and `javaParameters`.
    - Dynamic, dependency-based compiler args are also set via `StandardProjectConfigurations.configureFreeKotlinCompilerArgs()`. This is an annoying thing to have to do, but necessary because kotlinc will complain if you add opt-ins that are not recognized by any dependencies on that classpath.
  - Enabling K2 testing.
- Configure Detekt, if enabled, via `DetektTasks`.
- Configure Lint if not an Android project via the `com.android.lint` plugin and `LintTasks`.
- Configure the `src/{variant}/kotlin` source set in android projects, as  these are still not automatically enabled.
- Prevent use of the deprecated `android.extensions` extension.
- Configure kapt (if requested) with common configuration
  - `correctErrorTypes` is set to true for better error messages.
  - `mapDiagnosticLocations` is set to false because [it's broken](https://github.com/JetBrains/kotlin/pull/3610).
  - Unless opted-in, disables kapt in tests due to [this](https://youtrack.jetbrains.com/issue/KT-29481#focus=Comments-27-4651462.0-0).

## ApkVersioningPlugin

```kotlin
plugins {
  id("com.slack.gradle.apk-versioning")
}
```

This plugin is applied in Android application projects and is solely to configure the `versionCode` and `versionName` of APKs based on git and Gradle property inputs.

The following properties are sourced

```properties
versionMajor=...
versionMinor=...
versionPatch=...
```

This also adds a `generateVersionProperties` task that is more or less only relevant for Slack's internal CI.

## AGP Handlers

SGP is designed to work with multiple versions of AGP at a time, albeit only for forward compatibility and testing reasons. Generally SGP will only be tested against the latest stable version of AGP. To support multiple beta/canary versions of upcoming AGP versions, SGP has an API called `AgpHandler`, which is intended to be an AGP-agnostic common interface for configuring AGP projects across breaking API (source or binary) changes. When a new such change is introduced, we create an `AgpHandler{version}` artifact and implementation with that AGP version as its minimum. At runtime, SGP loads the relevant `AgpHandler` instance for the AGP version it is running against and relevant APIs use this instance via `FoundryTools` to interact with them in a version-agnostic way. These aren't always needed so there may be times when there are no implementations needed for the current suite of AGP versions.

An example handler for AGP 8.0 looks like this.

```kotlin
// AutoService makes it available via ServiceLoader
// The factory should always be AGP-api agnostic.
class AgpHandler80 : AgpHandler {
  @Suppress("DEPRECATION")
  override val agpVersion: String
    get() = com.android.builder.model.Version.ANDROID_GRADLE_PLUGIN_VERSION
  
  @AutoService(AgpHandlerFactory::class)
  class Factory : AgpHandlerFactory {
    override val minVersion: VersionNumber = VersionNumber.parse("8.0.0")
  
    @Suppress("DEPRECATION")
    override fun currentVersion(): String =
      com.android.builder.model.Version.ANDROID_GRADLE_PLUGIN_VERSION
  
    override fun create(): AgpHandler {
      return AgpHandler80()
    }
  }
}
```
