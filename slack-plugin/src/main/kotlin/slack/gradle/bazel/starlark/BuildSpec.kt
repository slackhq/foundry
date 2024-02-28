package slack.gradle.bazel.starlark

internal class BuildSpec {
  class Builder {
    val loads: LinkedHashMap<String, MutableList<String>> = LinkedHashMap()
  }
}
