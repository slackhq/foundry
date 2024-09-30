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
package foundry.cli.util

import com.squareup.moshi.adapter
import org.junit.Test
import slack.cli.shellsentry.ProcessingUtil

class SingleItemListAdapterTest {
  @Test
  fun singleItem() {
    val moshi = ProcessingUtil.newMoshi()

    val list = moshi.adapter<List<String>>().fromJson(""""foo"""")!!
    assert(list.size == 1)
    assert(list[0] == "foo")
  }
}
