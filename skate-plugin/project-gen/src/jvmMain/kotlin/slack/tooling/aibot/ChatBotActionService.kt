/*
 * Copyright (C) 2024 Slack Technologies, LLC
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
package slack.tooling.aibot

import com.google.gson.Gson
import com.google.gson.JsonObject
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.*

class ChatBotActionService {
  //  private val retrofit: Retrofit
  //
  //  init {
  //    val loggingInterceptor =
  //      HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }
  //
  //    val client = OkHttpClient.Builder().addInterceptor(loggingInterceptor).build()
  //
  //    retrofit =
  //      Retrofit.Builder()
  //        .baseUrl("https://jsonplaceholder.typicode.com/")
  //        .client(client)
  //        .addConverterFactory(GsonConverterFactory.create())
  //        .build()
  //  }

  //  interface JsonPlaceholderApi {
  //    @GET("posts")
  //    suspend fun getPosts(): List<Post>
  //  }

  //  private val api: JsonPlaceholderApi = retrofit.create(JsonPlaceholderApi::class.java)

  //  suspend fun executeCommand(userInput: String): String {
  //    println("Received input: '$userInput'. Fetching a random post.")
  //    return withContext(Dispatchers.IO) {
  //      try {
  //        val posts = api.getPosts()
  //        val randomPost = posts[Random.nextInt(posts.size)]
  //        """I received your message: "$userInput"
  //                Here's a random post for you:
  //                Title: ${randomPost.title}
  //                Body: ${randomPost.body}
  //                """.trimIndent()
  //      } catch (e: Exception) {
  //        println("Error fetching post: ${e.message}")
  //        e.printStackTrace()
  //        "I received your message, but I'm unable to fetch a post at this time. Error:
  // ${e.message}"
  //      }
  //    }
  //  }
  //
  //  data class Post(
  //    val id: Int,
  //    val title: String,
  //    val body: String,
  //    val userId: Int
  //  )
  suspend fun executeCommand(question: String): String {
    val jsonContent =
      """
        {
            "messages": [{"role": "user", "content": "$question"}],
            "source": "curl",
            "max_tokens": 2048
        }
    """
        .trimIndent()

    val scriptContent =
      """
        #!/bin/bash
        export PATH="/usr/local/bin:/usr/bin:${'$'}PATH"
        export SSH_OPTIONS="-T"

        /usr/local/bin/slack-uberproxy-curl -X POST https://devxp-ai-api.tinyspeck.com/v1/chat/ -H "Content-Type: application/json" -d '$jsonContent'
    """
        .trimIndent()

    return withContext(Dispatchers.IO) {
      val tempScript = File.createTempFile("run_command", ".sh")
      tempScript.writeText(scriptContent)
      tempScript.setExecutable(true)

      val process =
        ProcessBuilder("/bin/bash", tempScript.absolutePath).redirectErrorStream(true).start()

      val output = process.inputStream.bufferedReader().use { it.readText() }
      process.waitFor()

      tempScript.delete()

      val regex = """\{.*\}""".toRegex(RegexOption.DOT_MATCHES_ALL)
      val result = regex.find(output.toString())?.value ?: "{}"
      val gson = Gson()
      val jsonObject = gson.fromJson(result, JsonObject::class.java)
      val contentArray = jsonObject.getAsJsonArray("content")
      val contentObject = contentArray.get(0).asJsonObject
      val actualContent = contentObject.get("content").asString

      actualContent

      //        output
    }
  }
}
