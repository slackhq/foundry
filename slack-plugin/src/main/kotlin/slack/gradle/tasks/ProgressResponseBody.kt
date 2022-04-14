/*
 * Copyright (C) 2022 Slack Technologies, LLC
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
package slack.gradle.tasks

import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.Source
import okio.buffer

internal class ProgressResponseBody
internal constructor(
  private val responseBody: ResponseBody,
  private val progressListener: ProgressListener
) : ResponseBody() {

  private val bufferedSource: BufferedSource by lazy { source(responseBody.source()).buffer() }

  override fun contentType() = responseBody.contentType()

  override fun contentLength() = responseBody.contentLength()

  override fun source() = bufferedSource

  private fun source(source: Source): Source {
    return object : ForwardingSource(source) {
      var totalBytesRead = 0L
      override fun read(sink: Buffer, byteCount: Long): Long {
        val bytesRead: Long = super.read(sink, byteCount)
        // read() returns the number of bytes read, or -1 if this source is exhausted.
        totalBytesRead += if (bytesRead != -1L) bytesRead else 0
        progressListener.update(totalBytesRead, responseBody.contentLength(), bytesRead == -1L)
        return bytesRead
      }
    }
  }
}

internal interface ProgressListener {
  fun update(bytesRead: Long, contentLength: Long, done: Boolean)
}
