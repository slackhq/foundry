# Contributors Guide

Note that this project is considered READ-ONLY. You are welcome to discuss or ask questions in the
discussions section of the repo, but we do not normally accept external contributions without prior
discussion.

## Development

Check out this repo with Android Studio or IntelliJ. It's a standard gradle project and
conventional to check out.

The primary project is `slack-plugin`.

Kotlin should be used for more idiomatic use with Gradle/AGP APIs

Code formatting is checked via [Spotless](https://github.com/diffplug/spotless). To run the formatter,
use the `spotlessApply` command.

```bash
./gradlew spotlessApply -Dorg.gradle.unsafe.isolated-projects=false
```

> **Note**: The `-Dorg.gradle.unsafe.isolated-projects=false` flag is required because this project
> uses Gradle's isolated projects feature, which Spotless does not yet support.
> See [diffplug/spotless#1979](https://github.com/diffplug/spotless/issues/1979) for details.

### Building the CLI

The CLI module uses KSP for `@AutoService` annotations. Since KSP does not yet support isolated
projects, you must disable it when building the CLI:

```bash
./gradlew :tools:cli:build -Dorg.gradle.unsafe.isolated-projects=false
```

Without this flag, the CLI JAR will not contain the service loader files and CLI commands will not
be discoverable at runtime.

Optionally, there are commit hooks in the repo you can enable by running the below
```bash
git config core.hooksPath config/git/hooks
```