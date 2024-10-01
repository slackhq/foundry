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
package foundry.cli.shellsentry

import com.bugsnag.delivery.HttpDelivery
import com.bugsnag.serialization.Serializer
import java.io.IOException
import java.net.Proxy
import java.util.concurrent.TimeUnit
import okhttp3.Authenticator
import okhttp3.Headers.Companion.toHeaders
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import org.slf4j.LoggerFactory

/** An [OkHttpClient]-based implementation of Bugsnag's [HttpDelivery]. */
internal object OkHttpSyncHttpDelivery : HttpDelivery {
  private val LOGGER = LoggerFactory.getLogger(OkHttpSyncHttpDelivery::class.java)
  private const val DEFAULT_TIMEOUT = 5000L
  private const val ENDPOINT = "https://notify.bugsnag.com"

  override fun deliver(serializer: Serializer, payload: Any, headers: Map<String, String>) {
    val client =
      OkHttpClient.Builder()
        .callTimeout(DEFAULT_TIMEOUT, TimeUnit.MILLISECONDS)
        .proxyAuthenticator(Authenticator.JAVA_NET_AUTHENTICATOR)
        .build()
    val request =
      Request.Builder()
        .url(ENDPOINT)
        .headers(headers.toHeaders())
        .post(
          object : RequestBody() {
            override fun contentType() = "application/json".toMediaType()

            override fun writeTo(sink: BufferedSink) {
              sink.outputStream().use { serializer.writeToStream(it, payload) }
            }
          }
        )
        .build()

    try {
      client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
          LOGGER.warn(
            "Error not reported to Bugsnag - got non-200 response code: {}",
            response.code,
          )
        }
      }
    } catch (ex: IOException) {
      LOGGER.warn("Error not reported to Bugsnag - exception when making request", ex)
    }
  }

  override fun close() {
    // Nothing to do here.
  }

  override fun setEndpoint(endpoint: String) {
    // Unsupported here
  }

  override fun setTimeout(timeout: Int) {
    // Unsupported here
  }

  override fun setProxy(proxy: Proxy) {
    // Unsupported here
  }
}
