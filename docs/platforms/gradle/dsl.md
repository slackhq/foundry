DSL
===

SGP offers a DSL extension for configuring project behavior via the plugin. The idea is that developers don't really
want to think about specific dependency wirings, and instead want to express what _features_ they want and allow SGP to
automatically wire these up for them.

Some examples of this include Dagger, Moshi code gen, Robolectric, and more.

The primary entry point is the `slack` extension in the build file, which is backed by the `SlackExtension` interface.

```kotlin
foundry {
    features {
        dagger(...)
        moshi(...)
    }
    android {
        features {
            robolectric(...)
        }
    }
}
```

## Features

### Circuit

The Circuit feature automatically sets up [Circuit](https://github.com/slackhq/circuit) in the project. This includes
controls for different Circuit artifacts and code gen. The default `circuit()` call will just enable Circuit's
runtime + code gen.

### Dagger

The Dagger feature automatically sets up both Dagger and Anvil. This includes optional parameters to control whether or
not you want the runtime only, component merging, or other features. This automatically handles applying the Anvil,
kapt, or KSP plugins under the hood and any necessary dependencies to run them.

The default `dagger()` call will just enable Dagger's runtime + Anvil's factory generation with _no_ component merging (
to avoid the Kapt cost).

### Moshi

The Moshi feature handles setting up both Moshi and MoshiX. This includes handling applying code gen logic (either KSP
or IR) as well as `moshi-sealed` support if requested.

### Redacted

This enables the [redacted-compiler-plugin](https://github.com/zacsweers/redacted-compiler-plugin) compiler plugin.

### Compose

The Compose feature handles setting up Compose in both Android and multiplatform projects. This handles a bunch of
boilerplate (see `ComposeUtil.kt`) for applying the right compose-compiler artifact version as well as enabling the
right controls in the Android plugin.

### Test Fixtures

This enables Gradle test fixtures in a project-agnostic fashion. In JVM projects this will apply the `java-test-fixtures` plugin, in Android projects this will configure the `android.testFixtures.enable` property.

## Android Features

### Robolectric

The Robolectric feature handles setting up Robolectric in an Android project. This entails common Robolectric
dependencies (including any bundles or core Robolectric project dependencies). This also sets up Robolectric jar
downloads (via `UpdateRobolectricJarsTask`) for test tasks and enabling resource merging in tests (which Robolectric
requires). There are a few other controls that `StandardProjectConfigurations` use to control or patch Robolectric's
behavior.

### Android Test

By default, SGP disables androidTests in projects. These can be enabled via the `androidTest()` feature, which will enable the relevant controls in the Android plugin. This can also accept specified variants to enable/disable.

This is important for opting in tests to [AndroidTest APK Aggregation](utilities.md#androidtest-apk-aggregation).

### Resources

By default, we disable _Android_ resources (different from _Java_ resources) and libraries have to opt-in to using them.

This can be enabled via the `resources()` feature, which will enable the relevant `BuildFeature` in the Android plugin and also takes a required `prefix` parameter that is used as the required `resourcePrefix` for that library's resources to avoid naming conflicts.

## Android Application Features

### Permission AllowList

This enables checking of a permission allowlist. See [`PermissionChecks`](utilities.md#permissionchecks) for more
details.