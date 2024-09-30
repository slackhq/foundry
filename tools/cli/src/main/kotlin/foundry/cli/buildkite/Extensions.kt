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
package foundry.cli.buildkite

public fun artifacts(vararg artifactPaths: String): SimpleStringValue {
  val paths = artifactPaths.toList()
  return when (paths.size) {
    0 -> SimpleStringValue(emptyList())
    1 -> SimpleStringValue(paths[0])
    else -> SimpleStringValue(paths)
  }
}

public fun SimpleStringValue.coalesceToList(): List<String> =
  when (this) {
    is SimpleStringValue.ListValue -> value
    is SimpleStringValue.SingleValue -> listOf(value)
  }

public fun CommandStep.withAddedArtifacts(vararg newPaths: String): CommandStep {
  val current = artifactPaths?.coalesceToList().orEmpty()
  val new = newPaths.toList()
  return copy(artifactPaths = SimpleStringValue((current + new).distinct()))
}

public fun envMap(vararg env: Pair<String, String>): Map<String, String> {
  return buildMap {
    for ((key, value) in env) {
      put(key, value)
    }
  }
}

public fun CommandStep.withAddedEnv(vararg newEnv: Pair<String, String>): CommandStep {
  return copy(
    env =
      buildMap {
        for ((key, value) in env?.entries.orEmpty()) {
          put(key, value)
        }
        for ((key, value) in newEnv) {
          put(key, value)
        }
      }
  )
}

public object Conditions {
  public const val NOT_CANCELLING: String = "build.state != \"canceling\""
}

public fun githubStatusNotif(
  context: String,
  notifyIf: String = Conditions.NOT_CANCELLING,
): Notification =
  Notification(
    ExternalNotification(
      githubCommitStatus = GithubCommitStatus(context = context),
      notifyIf = notifyIf,
    )
  )

public fun CommandStep.withGithubStatus(context: String): CommandStep {
  return copy(notify = listOf(githubStatusNotif(context)))
}
