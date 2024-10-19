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
package foundry.tracing.api

import foundry.tracing.ListOfSpans
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Url

/** Represents a simple tracing API that accepts [ListOfSpans] proto bodies. */
internal interface TracingService {
  @POST suspend fun sendTrace(@Url url: String, @Body spans: ListOfSpans)
}
