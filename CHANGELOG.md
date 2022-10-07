Changelog
=========

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
