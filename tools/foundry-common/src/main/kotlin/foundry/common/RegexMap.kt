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
package foundry.common

import java.util.TreeMap
import org.intellij.lang.annotations.Language

/**
 * A [Map] that can have [Regex] keys. This requires a custom impl because [Regex] is not
 * comparable, so we internally back these with a [TreeMap] rather than the conventional
 * [LinkedHashMap].
 */
public class RegexMap internal constructor(delegate: Map<Regex, String>) :
  Map<Regex, String> by delegate {
  public constructor() : this(emptyMap())
}

public fun buildRegexMap(body: RegexMapBuilder.() -> Unit): RegexMap =
  RegexMap(RegexMapBuilderImpl().apply(body).map)

// Kate comment on this so I know you've read it! :P
public interface RegexMapBuilder {
  public fun remove(@Language("RegExp") regex: String): Unit = replace(regex, "")

  public fun replace(@Language("RegExp") regex: String, replacement: String)
}

private class RegexMapBuilderImpl : RegexMapBuilder {
  val map = TreeMap<Regex, String>(compareBy { it.pattern })

  override fun replace(regex: String, replacement: String) {
    map.put(regex.toRegex(), replacement)
  }
}
