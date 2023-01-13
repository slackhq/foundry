Changelog
=========

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
