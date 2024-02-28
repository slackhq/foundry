/*
 * Copyright (C) 2024 Slack Technologies, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package slack.gradle.bazel

/** A Bazel dependency. */
internal sealed interface Dep : Comparable<Dep> {
  override fun compareTo(other: Dep) = toString().compareTo(other.toString())

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

  /**
   * A target dependency, e.g. `:main_lib`.
   *
   * @param name the name to depend on, e.g. `main_lib`
   */
  data class Target(val name: String) : Dep {
    override fun toString(): String {
      return ":$name"
    }
  }
}
