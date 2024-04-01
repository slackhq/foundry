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
package slack.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlin.Int
import kotlin.String

@JsonClass(generateAdapter = true)
data class Call(
  val id: String,
  @Json(name = "date_start") val dateStart: String,
  @Json(name = "date_end") val dateEnded: String? = null,
  @Json(name = "active_participant_count") val activeParticipantCount: Int = 0,
  val title: Title? = null,
  @Json(name = "custom_title") val customTitle: String? = null,
  @Json(name = "outgoing") val outgoingToUser: CallUser? = null,
  @Json(name = "incoming") val incomingFromUser: CallUser? = null,
  @Json(name = "active_participants") val activeParticipants: List<String>,
  val actions: List<Action>? = null,
  @Json(name = "retry_text") val retryText: RetryText? = null,
) {
  @JsonClass(generateAdapter = false)
  enum class Action {
    UNKNOWN,
    @Json(name = "join") JOIN,
    @Json(name = "decline") DECLINE,
    @Json(name = "retry") RETRY
  }

  @JsonClass(generateAdapter = false)
  enum class RetryText {
    UNKNOWN,
    @Json(name = "call_back") CALL_BACK,
    @Json(name = "call_again") CALL_AGAIN
  }

  @JsonClass(generateAdapter = false)
  enum class Title {
    UNKNOWN,
    @Json(name = "custom") CUSTOM,
    @Json(name = "incoming_ringing") INCOMING_RINGING,
    @Json(name = "outgoing_ringing") OUTGOING_RINGING,
    @Json(name = "active") ACTIVE,
    @Json(name = "ended") ENDED,
    @Json(name = "missed") MISSED,
    @Json(name = "declined") DECLINED
  }

  @JsonClass(generateAdapter = true)
  data class Transcription(val status: Status?, val lines: List<String>? = emptyList()) {
    @JsonClass(generateAdapter = false)
    enum class Status {
      // Indicates a missing or unrecognized value
      UNKNOWN,
      @Json(name = "complete") COMPLETE,
      @Json(name = "failed") FAILED,
      @Json(name = "processing") PROCESSING
    }
  }
}
