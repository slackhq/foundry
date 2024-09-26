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
package foundry.tracing.reporter

import foundry.tracing.ListOfSpans

/** Reports a build trace (modeled as list of spans, in protocol buffer format) to some location. */
public interface TraceReporter {
  public suspend fun sendTrace(spans: ListOfSpans)

  public object NoOpTraceReporter : TraceReporter {
    override suspend fun sendTrace(spans: ListOfSpans) {
      // Do nothing!
    }
  }
}
