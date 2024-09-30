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

import com.squareup.moshi.JsonClass
import dev.zacsweers.moshix.sealed.annotations.TypeLabel
import kotlin.time.Duration

@JsonClass(generateAdapter = true, generator = "sealed:type")
public sealed interface RetrySignal {

  /** Unknown issue. */
  @TypeLabel("unknown") public data object Unknown : RetrySignal

  /** Indicates an issue that is recognized but cannot be retried. */
  @TypeLabel("ack") public data object Ack : RetrySignal

  /** Indicates this issue should be retried immediately. */
  @TypeLabel("immediate") public data object RetryImmediately : RetrySignal

  /** Indicates this issue should be retried after a [delay]. */
  @TypeLabel("delayed")
  @JsonClass(generateAdapter = true)
  public data class RetryDelayed(
    // Can't default to 1.minutes due to https://github.com/ZacSweers/MoshiX/issues/442
    val delay: Duration
  ) : RetrySignal
}
