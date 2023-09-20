Artifactory Authenticator
=========================

A simple IntelliJ plugin for authenticating plugin repositories that live on Artifactory and require authentication. We use this at Slack to authenticate our internal plugin repository and publish updates there.

This uses the `PluginRepositoryAuthProvider` API.

## Usage

Once installed, the artifactory repository can be configured in Settings > Artifactory Auth Settings. Fill in the information there and enable it.

The check is a prefix check, so you should use a base url for your artifactory instance.

## Publishing

To publish this plugin to a given artifactory repository, the following three Gradle properties must be set:

```properties
SgpIntellijArtifactoryBaseUrl=https://artifactory.example.com/artifactory
SgpIntellijArtifactoryUsername=jane.doe@example.com
SgpIntellijArtifactoryToken=1234567890abcdef1234567890abcdef12345678
```

Then run `./gradlew :skate-plugin:artifactory-authenticator:uploadPluginToArtifactory` to publish the plugin to the configured repository.
