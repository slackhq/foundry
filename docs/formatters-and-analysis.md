Formatters and Static Analysis
==============================

SGP supports running a number of formatters and static analysis tools.

Individual tools are usually gated on whether they have a version specified in `libs.versions.toml`. If they do not have a version specified, they are deemed not enabled.

## Formatting

The core set of formatters are:

- ktfmt (Kotlin)
- google-java-format (Java)
- gson (JSON)
- gradle-dependency-sorter (Gradle build file dependencies)
- Spotless (general purpose Gradle plugin that runs most of the above)

## Static Analysis

The core set of analysis tools supported in SGP are:

- Android Lint (Kotlin, Java, XML resources, build files, etc.)
- Detekt (Kotlin)
- Error Prone (Java)

## Git Hooks

SGP ships with a standard set of git hooks (pre-commit, etc) that it can bootstrap in projects by running `./gradlew installCommitHooks`. These hooks rely on checking in relevant binaries for each formatter/checker, it's strongly recommended to use git-lfs for these. These files should be edited as needed to best serve the project they're running in.

SGP can configure these hooks in the project automatically during bootstrap if you add the `slack.git.hooksPath` gradle property and point it at the hooks directory that the above command output to, or wherever the host project opts to store them.

Note that Detekt is not yet supported in git hooks as these require extra parameters for baselines.

### Downloading binaries

Each tool (ktfmt, gjf, etc) has corresponding `./gradlew update<tool name>` tasks that you can run to download and install them, by default to `config/bin/<tool name>`. You should re-run these any time you update a tool to re-run them.