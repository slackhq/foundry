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
./gradlew spotlessApply
```