Bootstrap
=========

Bootstrap is a tool for bootstrapping local dev environments. This is usually used in tandem with a bootstrap bash
script that runs the `./gradlew bootstrap` task and any other repo-specific setups.

The core implementation lives in `BootstrapTask.kt`.

At a high level, bootstrap is mostly focused on configuring the JDK and daemon environments. Gradle has extremely
limited configurability for the Gradle daemon, and we want to optimize the JDK for available space on different
developer machines. To support this, we compute optimal daemon jvm arguments in bootstrap and write them to the user's
home `~/.gradle/gradle.properties` to override repo-specific settings with client-side properties.

For the JDK, it requests the JDK toolchain from Gradle's first-party APIs. This includes allowing Gradle to download the
JDK if it's missing, which is useful for getting developers set up and running faster.

Bootstrap is also useful on CI for its ability to scale available memory to the machine it's running on, so we generally
run it as a preflight step for all of our CI jobs too.

Finally, there are some other specific things it does to optimize things:

- Disable Gradle file watching on CI as it's not necessary there.
- Configure specific GC and heap args for optimized use in Gradle builds (favoring larger young generation spaces).
- Configure a fixed-size heap for CI to avoid time spent growing the heap.
