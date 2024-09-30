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
package slack.cli.shellsentry

import com.github.ajalt.clikt.core.parse
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ShellSentryCliTest {

  @JvmField @Rule val temporaryFolder = TemporaryFolder()

  @Test
  fun standardParsing() {
    val configFile = temporaryFolder.newFile("config.json")
    val args = "./gradlew build -Pvariant=debug"
    val parsed =
      ShellSentryCli().apply {
        parse(
          arrayOf(
            "--project-dir",
            temporaryFolder.root.absolutePath,
            "--bugsnag-key=1234",
            "--verbose",
            "--parse-only",
            "--config",
            configFile.absolutePath,
            "--",
            args,
          )
        )
      }
    assertThat(parsed.projectDir).isEqualTo(temporaryFolder.root.toPath())
    assertThat(parsed.verbose).isTrue()
    assertThat(parsed.bugsnagKey).isEqualTo("1234")
    assertThat(parsed.configurationFile).isEqualTo(configFile.toPath())
    assertThat(parsed.args).isEqualTo(listOf(args))
  }
}
