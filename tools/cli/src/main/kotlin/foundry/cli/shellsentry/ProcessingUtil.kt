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

import com.squareup.moshi.Moshi
import com.squareup.moshi.addAdapter
import foundry.cli.util.DurationJsonAdapter
import foundry.cli.util.RegexJsonAdapter
import foundry.cli.util.SingleItemListJsonAdapterFactory

internal object ProcessingUtil {
  fun newMoshi(): Moshi {
    return Moshi.Builder()
      .add(SingleItemListJsonAdapterFactory())
      .addAdapter(DurationJsonAdapter())
      .add(RegexJsonAdapter.Factory())
      .build()
  }
}
