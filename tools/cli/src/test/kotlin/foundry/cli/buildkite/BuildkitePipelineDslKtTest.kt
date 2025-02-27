/*
 * Copyright (C) 2025 Slack Technologies, LLC
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

import com.charleskorn.kaml.MultiLineStringStyle
import com.charleskorn.kaml.SingleLineStringStyle
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.google.common.truth.Truth.assertThat
import org.intellij.lang.annotations.Language
import org.junit.Test

class BuildkitePipelineDslKtTest {

  @Test
  fun `test pipeline with single command step`() {
    val pipeline = pipeline {
      commandStep {
        label = "Test Step"
        command = "echo 'Hello, World!'"
      }
    }

    pipeline.assertYaml(
      """
      steps:
      - label: Test Step
        command:  'echo ''Hello, World!'''
      """
        .trimIndent()
    )
  }

  @Test
  fun `test pipeline with multiple steps`() {
    val pipeline = pipeline {
      commandStep {
        label = "Step 1"
        command = "echo 'Step 1'"
      }
      commandStep {
        label = "Step 2"
        commands("echo 'Step 2'", "echo 'Another Command'")
      }
      waitStep()
    }

    pipeline.assertYaml(
      """
      steps:
      - label: Step 1
        command:  'echo ''Step 1'''
      - label: Step 2
        commands:
        -  'echo ''Step 2'''
        - echo 'Another Command'
      - wait: ~
        continue_on_failure: true
      """
        .trimIndent()
    )
  }

  @Test
  fun `test pipeline with agents`() {
    val pipeline = pipeline { agents { agent { "queue" to "default" } } }

    pipeline.assertYaml(
      """
      steps: []
      agents:
        queue: default
      """
        .trimIndent()
    )
  }

  @Test
  fun `test pipeline with environment variables`() {
    val pipeline = pipeline {
      env {
        "KEY1" to "VALUE1"
        "KEY2" to "VALUE2"
      }
    }

    pipeline.assertYaml(
      """
      steps: []
      env:
        KEY1: VALUE1
        KEY2: VALUE2
      """
        .trimIndent()
    )
  }

  @Test
  fun `test pipeline with nested group steps`() {
    val pipeline = pipeline {
      group("Group 1") {
        commandStep {
          label = "Step 1"
          command = "echo 'Step 1 in Group 1'"
        }
      }
      group("Group 2") {
        commandStep {
          label = "Step 2"
          command = "echo 'Step 2 in Group 2'"
        }
      }
      waitStep()
    }

    pipeline.assertYaml(
      """
      steps:
      - group: Group 1
        steps:
        - label: Step 1
          command:  'echo ''Step 1 in Group 1'''
      - group: Group 2
        steps:
        - label: Step 2
          command:  'echo ''Step 2 in Group 2'''
      - wait: ~
        continue_on_failure: true
      """
        .trimIndent()
    )
  }

  private fun Pipeline.assertYaml(@Language("YAML") expectedYaml: String) {
    assertThat(toYaml()).isEqualTo(expectedYaml)
  }

  private fun Pipeline.toYaml(): String {
    val yamlConfig =
      YamlConfiguration(
        encodeDefaults = false,
        breakScalarsAt = 200,
        singleLineStringStyle = SingleLineStringStyle.Plain,
        multiLineStringStyle = MultiLineStringStyle.Literal,
      )
    val yaml = Yaml(configuration = yamlConfig)
    // TODO temporary until https://github.com/charleskorn/kaml/pull/494 is available
    val tagRegexList = """- !<[^>]*>(\n\s*)?""".toRegex()
    val tagRegexBlock = """!<[^>]*>""".toRegex()
    // TODO temporary, as buildkite's schema says strings are allowed but its validator does not
    // Replace "exit_status:  '-1'" with "exit_status:  -1"
    val exitStatusFixRegex = """(exit_status:\s*)'([^']*)'""".toRegex()
    val yamlText =
      yaml
        .encodeToString(Pipeline.serializer(), this)
        .replace(tagRegexList, "- ")
        .replace(tagRegexBlock, "")
        .replace(exitStatusFixRegex) { matchResult ->
          "${matchResult.groupValues[1]}${matchResult.groupValues[2]}"
        }
    return yamlText
  }
}
