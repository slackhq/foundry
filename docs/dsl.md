DSL
===

SGP offers a DSL extension for configuring project behavior via the plugin. The idea is that developers don't really
want to think about specific dependency wirings, and instead want to express what _features_ they want and allow SGP to
automatically wire these up for them.

Some examples of this include Dagger, Moshi code gen, Robolectric, and more.

The primary entry point is the `slack` extension in the build file, which is backed by the `SlackExtension` interface.

```kotlin
slack {
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

## Android Features

### Robolectric

The Robolectric feature handles setting up Robolectric in an Android project. This entails common Robolectric
dependencies (including any bundles or core Robolectric project dependencies). This also sets up Robolectric jar
downloads (via `UpdateRobolectricJarsTask`) for test tasks and enabling resource merging in tests (which Robolectric
requires). There are a few other controls that `StandardProjectConfigurations` use to control or patch Robolectric's
behavior.

### Android Test

By default, SGP disables androidTests in projects. These can be enabled via the `androidTest()` feature, which will enable the relevant controls in the Android plugin. This can also accept specified variants to enable/disable.

This is important for opting in tests to [AndroidTest APK Aggregation](/utilities/#androidtest-apk-aggregation).

## Android Application Features

### Permission AllowList

This enables checking of a permission allowlist. See [`PermissionChecks`](/utilities/#permissionchecks) for more
details.