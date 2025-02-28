Skate Plugin
=========================

An Intellij plugin that helps to improve local developer productivity by surfacing useful information in the IDE.

We use this at Slack for several use cases:
* Announce updates, latest changes and improvements through "What's New" panel
* Annotate Feature Flag to help easily access its setup
* Generate API model translators when migrating legacy API
* Create initial new subproject setup from `File` dropdown

## Installation

#### Artifactory
1. Install `artifactory-authenticator` plugin from disk and authenticate with Artifactory
2. Add custom plugin repository link from "Manage Plugin Repositories"
3. Search "Skate" in the plugins marketplace and install it

#### Local testing with zip
1. Build local version of the plugin with `./gradlew buildPlugin`
2. Open IDE settings, then "Install Plugin from Disk..."

#### Local testing from CLI
1. Add your local Android Studio path to your `~/.gradle/gradle.properties` file like this: `intellijPlatformTesting.idePath=/Users/username/Applications/Android Studio.app`
2. Run `./gradlew :platforms:intellij:skate:runLocalIde` from the CLI and it will open an Android Studio instance

## Implementation
All registered plugin actions can be found in `skate.xml` config file

## Tracing
We're sending analytics for almost all Skate features to track user usage. To set this up,
1. Register new feature event in `SkateTracingEvent`
2. Use `SkateSpanBuilder` to create the span for event you want to track
3. Make call to `SkateTraceReporter` to send up the traces

## Releasing
1. Update `change-notes.html` file under `skate/` and merge it to `main`
2. Run `publish-skate` Github Action.
Behind the scene the action's running`./gradlew :platforms:intellij:skate:uploadPluginToArtifactory`
