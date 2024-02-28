package slack.gradle.bazel

/** A Bazel dependency. */
internal sealed interface Dep {
  /**
   * A remote dependency, e.g. `@maven//path:target`.
   *
   * @param source the source of the dependency, e.g. `maven`
   * @param path the path to the dependency, e.g. `path`
   * @param target the target of the dependency, e.g. `target`
   */
  data class Remote(val source: String = "maven", val path: String = "", val target: String) : Dep {
    override fun toString(): String {
      return "@$source//$path:$target"
    }
  }

  /**
   * A local dependency, e.g. `//path/to/local/dependency1`.
   *
   * @param path the path to the local dependency, e.g. `path/to/local/dependency1`
   */
  data class Local(val path: String) : Dep {
    override fun toString(): String {
      return "//$path"
    }
  }

  companion object COMPARATOR : Comparator<Dep> by compareBy(Dep::toString)
}
