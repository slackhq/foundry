/*
 * Copyright (C) 2023 Slack Technologies, LLC
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
package com.slack.sgp.common

public fun <T, R> Collection<T>.mapToSet(transform: (T) -> R): Set<R> {
  return mapTo(mutableSetOf(), transform)
}

public fun <T, R> Collection<T>.flatMapToSet(transform: (T) -> Iterable<R>): Set<R> {
  return flatMapTo(mutableSetOf(), transform)
}

/**
 * Flips a map. In the context of `ComputeAffectedProjectsTask`, we use this to flip a map of
 * projects to their dependencies to a map of projects to the projects that depend on them. We use
 * this to find all affected projects given a seed of changed projects.
 *
 * Example:
 *
 *  ```
 *  Given a map
 *  {a:[b, c], b:[d], c:[d], d:[]}
 *  return
 *  {b:[a], c:[a], d:[b, c]}
 *  ```
 */
public fun Map<String, Set<String>>.flip(): Map<String, Set<String>> {
  val flipped = mutableMapOf<String, MutableSet<String>>()
  for ((project, dependenciesSet) in this) {
    for (dependency in dependenciesSet) {
      flipped.getOrPut(dependency, ::mutableSetOf).add(project)
    }
  }
  return flipped
}
