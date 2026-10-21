/*
 * Copyright (C) 2026 Slack Technologies, LLC
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
package foundry.cli.sarif

import com.google.common.truth.Truth.assertThat
import io.github.detekt.sarif4k.ArtifactLocation
import io.github.detekt.sarif4k.Location
import io.github.detekt.sarif4k.Message
import io.github.detekt.sarif4k.PhysicalLocation
import io.github.detekt.sarif4k.Region
import io.github.detekt.sarif4k.ReportingDescriptor
import io.github.detekt.sarif4k.Result
import io.github.detekt.sarif4k.Run
import io.github.detekt.sarif4k.SarifSchema210
import io.github.detekt.sarif4k.Tool
import io.github.detekt.sarif4k.ToolComponent
import io.github.detekt.sarif4k.Version
import org.junit.Test

class SarifUtilTest {

  @Test
  fun `merge keeps distinct results whose hash codes collide`() {
    // "Aa" and "BB" have the same String.hashCode(), so the old Objects.hash keys collided.
    assertThat("Aa".hashCode()).isEqualTo("BB".hashCode())
    val first = result(message = "Aa")
    val second = result(message = "BB")

    val merged = listOf(sarif(first), sarif(second)).merge(removeUriPrefixes = true) {}

    assertThat(merged.results().map { it.message.text }).containsExactly("Aa", "BB")
  }

  @Test
  fun `merge result does not depend on input order`() {
    val first = result(uri = "src/Aa.kt")
    val second = result(uri = "src/BB.kt")

    val forward = listOf(sarif(first), sarif(second)).merge(removeUriPrefixes = true) {}
    val reverse = listOf(sarif(second), sarif(first)).merge(removeUriPrefixes = true) {}

    assertThat(forward.results()).hasSize(2)
    assertThat(forward.results()).isEqualTo(reverse.results())
  }

  @Test
  fun `merge removes identical results from multiple reports`() {
    val merged =
      listOf(sarif(result()), sarif(result()), sarif(result(startLine = 2))).merge(
        removeUriPrefixes = true
      ) {}

    assertThat(merged.results().map { it.locations?.single()?.physicalLocation?.region?.startLine })
      .containsExactly(1L, 2L)
  }

  @Test
  fun `identityKey differs for locations whose hash codes collide`() {
    assertThat(result(uri = "src/Aa.kt").identityKey)
      .isNotEqualTo(result(uri = "src/BB.kt").identityKey)
  }

  @Test
  fun `identityKey ignores message but shallowKey does not`() {
    val first = result(message = "one")
    val second = result(message = "two")

    assertThat(first.identityKey).isEqualTo(second.identityKey)
    assertThat(first.shallowKey).isNotEqualTo(second.shallowKey)
  }

  private fun SarifSchema210.results(): List<Result> = runs.single().results.orEmpty()

  private fun sarif(vararg results: Result): SarifSchema210 =
    SarifSchema210(
      version = Version.The210,
      runs =
        listOf(
          Run(
            tool =
              Tool(ToolComponent(name = "lint", rules = listOf(ReportingDescriptor(id = RULE)))),
            results = results.toList(),
          )
        ),
    )

  private fun result(
    message: String = "message",
    uri: String = "src/Foo.kt",
    startLine: Long = 1,
  ): Result =
    Result(
      ruleID = RULE,
      message = Message(text = message),
      locations =
        listOf(
          Location(
            physicalLocation =
              PhysicalLocation(
                artifactLocation = ArtifactLocation(uri = uri),
                region = Region(startLine = startLine, startColumn = 1),
              )
          )
        ),
    )
}

private const val RULE = "DeprecatedCall"
