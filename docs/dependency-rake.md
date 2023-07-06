Dependency Rake
===============

Dependency rake is an tool we develop within slack-gradle-plugin (SGP) to automatically clean up Gradle build files.

This tool uses the outputs of
the [dependency-analysis-gradle-plugin](https://github.com/autonomousapps/dependency-analysis-android-gradle-plugin) (
DAGP) to infer and apply fixes it recommends.

## Types of Fixes

There are three main categories of fixes that DR applies.

1. Remove unused dependencies.
    - Like it says on the tin, these are declared dependencies that are unused and should be removed.
2. Fix misused dependencies.
    - These are dependencies that are declared but only transitive dependencies they include are actually used. For
      example - depending on RxAndroid but only using RxJava from it. These are fixed by replacing the dependency with
      the specific transitive dependency.
3. Fix wrong configurations.
    - These are dependencies that declare the wrong Gradle configuration (e.g. api, implementation, etc). Dependencies
      determined to be used in the “public ABI” of the library should be “api” configuration for use in dependent
      projects. Anything else can be hidden as an “implementation” or “compileOnly” (if annotations-only) dependency.

## Benefits

The primary benefit of dependency rake is to improve build times by more or less “raking” the build dependency graph. By
removing and fixing dependencies, we remove unneeded edges in the build graph. This in turn provides improved build
parallelism and better avoidance in Skippy CI pipelines.

A secondary benefit is automatic upkeep of build files. As projects change over time, dependencies become obsolete and
out of date. Most developers do not keep up with these changes over time, so automating this affords us extra upkeep
that we currently do not do.

## Implementation

The core implementation of DR lives in `DependencyRake.kt`.

## Usage

To run dependency rake in a project, use the below command

```bash
$ ./gradlew rakeDependencies -Pslack.gradle.config.enableAnalysisPlugin=true --no-configuration-cache
```

This will run all `rakeDependencies` tasks in the project. This task exists on all subprojects as well, but it 
works best if all are run together.

Sometimes dependency rake will try to replace identifiers with ones that are not present in any available
version catalogs. Sometimes this is acceptable, but often times it can result in "missing" dependencies from 
the build after it runs. To help fix these, DR will write all missing identifiers out to a build output file.

For convenience, you can also run `./gradlew aggregateMissingIdentifiers -Pslack.gradle.config.enableAnalysisPlugin=true --no-configuration-cache`
to run all dependency rake tasks and aggregate these missing identifiers into a root project build output file.
