Module
======

Robolectric hosts instrumented Android API jars on maven central at specific coordinates that are often changed/tied to the robolectric version. To simplify management of robolectric, we hide their access behind a small SDK helper that can load them from Robolectric's JVM jar. We then use this in the Foundry gradle plugin to create resolvable configurations to load their coordinates via traditional maven resolution and store them in a "known" location.
