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
package slack.gradle.avoidance

import java.util.regex.PatternSyntaxException
import okio.Path

internal fun interface PathMatcher {
  fun matches(path: String): Boolean
}

internal fun PathMatcher.matches(path: Path) = matches(path.toString())

// TODO if we ever support windows, there's a separate windows method.
internal fun String.toPathMatcher(): PathMatcher {
  val regex = Globs.toUnixRegexPattern(this).toRegex()
  return PathMatcher(regex::matches)
}

// Copied from the JDK Globs.java and is used by the JDK's PathMatcher, but it's not exposed as
// public API for reuse.
private object Globs {
  private const val REGEX_META_CHARS = ".^$+{[]|()"
  private const val GLOB_META_CHARS = "\\*?[{"

  private val Char.isRegexMeta: Boolean
    get() {
      return REGEX_META_CHARS.indexOf(this) != -1
    }

  private val Char.isGlobMeta: Boolean
    get() {
      return GLOB_META_CHARS.indexOf(this) != -1
    }

  private const val EOL = 0.toChar() // TBD

  private fun next(glob: String, i: Int): Char {
    return if (i < glob.length) {
      glob[i]
    } else EOL
  }

  /**
   * Creates a regex pattern from the given glob expression.
   *
   * @throws PatternSyntaxException
   */
  private fun toRegexPattern(globPattern: String, isDos: Boolean): String {
    var inGroup = false
    val regex = StringBuilder("^")
    var i = 0
    while (i < globPattern.length) {
      var c = globPattern[i++]
      when (c) {
        '\\' -> {
          // escape special characters
          if (i == globPattern.length) {
            throw PatternSyntaxException("No character to escape", globPattern, i - 1)
          }
          val next = globPattern[i++]
          if (next.isGlobMeta || next.isRegexMeta) {
            regex.append('\\')
          }
          regex.append(next)
        }
        '/' ->
          if (isDos) {
            regex.append("\\\\")
          } else {
            regex.append(c)
          }
        '[' -> {
          // don't match name separator in class
          if (isDos) {
            regex.append("[[^\\\\]&&[")
          } else {
            regex.append("[[^/]&&[")
          }
          if (next(globPattern, i) == '^') {
            // escape the regex negation char if it appears
            regex.append("\\^")
            i++
          } else {
            // negation
            if (next(globPattern, i) == '!') {
              regex.append('^')
              i++
            }
            // hyphen allowed at start
            if (next(globPattern, i) == '-') {
              regex.append('-')
              i++
            }
          }
          var hasRangeStart = false
          var last = 0.toChar()
          while (i < globPattern.length) {
            c = globPattern[i++]
            if (c == ']') {
              break
            }
            if (c == '/' || isDos && c == '\\') {
              throw PatternSyntaxException("Explicit 'name separator' in class", globPattern, i - 1)
            }
            // TBD: how to specify ']' in a class?
            if (c == '\\' || c == '[' || c == '&' && next(globPattern, i) == '&') {
              // escape '\', '[' or "&&" for regex class
              regex.append('\\')
            }
            regex.append(c)
            if (c == '-') {
              if (!hasRangeStart) {
                throw PatternSyntaxException("Invalid range", globPattern, i - 1)
              }
              if (next(globPattern, i++).also { c = it } == EOL || c == ']') {
                break
              }
              if (c < last) {
                throw PatternSyntaxException("Invalid range", globPattern, i - 3)
              }
              regex.append(c)
              hasRangeStart = false
            } else {
              hasRangeStart = true
              last = c
            }
          }
          if (c != ']') {
            throw PatternSyntaxException("Missing ']", globPattern, i - 1)
          }
          regex.append("]]")
        }
        '{' -> {
          if (inGroup) {
            throw PatternSyntaxException("Cannot nest groups", globPattern, i - 1)
          }
          regex.append("(?:(?:")
          inGroup = true
        }
        '}' ->
          if (inGroup) {
            regex.append("))")
            inGroup = false
          } else {
            regex.append('}')
          }
        ',' ->
          if (inGroup) {
            regex.append(")|(?:")
          } else {
            regex.append(',')
          }
        '*' ->
          if (next(globPattern, i) == '*') {
            // crosses directory boundaries
            regex.append(".*")
            i++
          } else {
            // within directory boundary
            if (isDos) {
              regex.append("[^\\\\]*")
            } else {
              regex.append("[^/]*")
            }
          }
        '?' ->
          if (isDos) {
            regex.append("[^\\\\]")
          } else {
            regex.append("[^/]")
          }
        else -> {
          if (c.isRegexMeta) {
            regex.append('\\')
          }
          regex.append(c)
        }
      }
    }
    if (inGroup) {
      throw PatternSyntaxException("Missing '}", globPattern, i - 1)
    }
    return regex.append('$').toString()
  }

  fun toUnixRegexPattern(globPattern: String): String {
    return toRegexPattern(globPattern, false)
  }

  fun toWindowsRegexPattern(globPattern: String): String {
    return toRegexPattern(globPattern, true)
  }
}
