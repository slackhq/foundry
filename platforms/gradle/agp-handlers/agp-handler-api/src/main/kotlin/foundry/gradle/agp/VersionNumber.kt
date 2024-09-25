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
package foundry.gradle.agp

import com.google.common.collect.Ordering
import java.util.Locale
import java.util.Objects

/**
 * This class is only here to maintain binary compatibility with existing plugins.
 *
 * This is a copy of Gradle's now-deprecated (no idea why) version.
 */
public class VersionNumber
private constructor(
  public val major: Int,
  public val minor: Int,
  public val micro: Int,
  public val patch: Int,
  public val qualifier: String?,
  private val scheme: AbstractScheme,
) : Comparable<VersionNumber> {

  public constructor(
    major: Int,
    minor: Int,
    micro: Int,
    qualifier: String?,
  ) : this(major, minor, micro, 0, qualifier, DEFAULT_SCHEME)

  public constructor(
    major: Int,
    minor: Int,
    micro: Int,
    patch: Int,
    qualifier: String?,
  ) : this(major, minor, micro, patch, qualifier, PATCH_SCHEME)

  public val baseVersion: VersionNumber
    get() = VersionNumber(major, minor, micro, patch, null, scheme)

  override fun compareTo(other: VersionNumber): Int {
    if (major != other.major) {
      return major - other.major
    }
    if (minor != other.minor) {
      return minor - other.minor
    }
    if (micro != other.micro) {
      return micro - other.micro
    }
    return if (patch != other.patch) {
      patch - other.patch
    } else {
      Ordering.natural<Comparable<*>>()
        .nullsLast<Comparable<*>>()
        .compare(toLowerCase(qualifier), toLowerCase(other.qualifier))
    }
  }

  override fun equals(other: Any?): Boolean {
    return other is VersionNumber && compareTo(other) == 0
  }

  override fun hashCode(): Int {
    var result = major
    result = 31 * result + minor
    result = 31 * result + micro
    result = 31 * result + patch
    result = 31 * result + Objects.hashCode(qualifier)
    return result
  }

  override fun toString(): String {
    return scheme.format(this)
  }

  private fun toLowerCase(string: String?): String? {
    return string?.lowercase(Locale.US)
  }

  /** Returns the version number scheme. */
  public interface Scheme {
    public fun parse(value: String?): VersionNumber

    public fun format(versionNumber: VersionNumber): String
  }

  private abstract class AbstractScheme protected constructor(val depth: Int) : Scheme {
    override fun parse(value: String?): VersionNumber {
      if (value == null || value.isEmpty()) {
        return UNKNOWN
      }
      val scanner = Scanner(value)
      var minor = 0
      var micro = 0
      var patch = 0
      if (!scanner.hasDigit()) {
        return UNKNOWN
      }
      val major: Int = scanner.scanDigit()
      if (scanner.isSeparatorAndDigit('.')) {
        scanner.skipSeparator()
        minor = scanner.scanDigit()
        if (scanner.isSeparatorAndDigit('.')) {
          scanner.skipSeparator()
          micro = scanner.scanDigit()
          if (depth > 3 && scanner.isSeparatorAndDigit('.', '_')) {
            scanner.skipSeparator()
            patch = scanner.scanDigit()
          }
        }
      }
      if (scanner.isEnd) {
        return VersionNumber(major, minor, micro, patch, null, this)
      }
      if (scanner.isQualifier) {
        scanner.skipSeparator()
        return VersionNumber(major, minor, micro, patch, scanner.remainder(), this)
      }
      return UNKNOWN
    }

    private class Scanner(val str: String) {
      var pos = 0

      fun hasDigit(): Boolean {
        return pos < str.length && Character.isDigit(str[pos])
      }

      fun isSeparatorAndDigit(vararg separators: Char): Boolean {
        return pos < str.length - 1 && oneOf(*separators) && Character.isDigit(str[pos + 1])
      }

      private fun oneOf(vararg separators: Char): Boolean {
        val current = str[pos]
        for (element in separators) {
          if (current == element) {
            return true
          }
        }
        return false
      }

      val isQualifier: Boolean
        get() = pos < str.length - 1 && oneOf('.', '-')

      fun scanDigit(): Int {
        val start = pos
        while (hasDigit()) {
          pos++
        }
        return str.substring(start, pos).toInt()
      }

      val isEnd: Boolean
        get() = pos == str.length

      fun skipSeparator() {
        pos++
      }

      fun remainder(): String? {
        return if (pos == str.length) null else str.substring(pos)
      }
    }
  }

  private class DefaultScheme : AbstractScheme(3) {
    override fun format(versionNumber: VersionNumber): String {
      return VERSION_TEMPLATE.format(
        Locale.US,
        versionNumber.major,
        versionNumber.minor,
        versionNumber.micro,
        if (versionNumber.qualifier == null) "" else "-" + versionNumber.qualifier,
      )
    }

    companion object {
      private const val VERSION_TEMPLATE = "%d.%d.%d%s"
    }
  }

  private class SchemeWithPatchVersion : AbstractScheme(4) {
    override fun format(versionNumber: VersionNumber): String {
      return VERSION_TEMPLATE.format(
        Locale.US,
        versionNumber.major,
        versionNumber.minor,
        versionNumber.micro,
        versionNumber.patch,
        if (versionNumber.qualifier == null) "" else "-" + versionNumber.qualifier,
      )
    }

    companion object {
      private const val VERSION_TEMPLATE = "%d.%d.%d.%d%s"
    }
  }

  public companion object {
    private val DEFAULT_SCHEME = DefaultScheme()
    private val PATCH_SCHEME = SchemeWithPatchVersion()
    public val UNKNOWN: VersionNumber = version(0)

    @JvmOverloads
    public fun version(major: Int, minor: Int = 0): VersionNumber {
      return VersionNumber(major, minor, 0, 0, null, DEFAULT_SCHEME)
    }

    /** Returns the default MAJOR.MINOR.MICRO-QUALIFIER scheme. */
    public fun scheme(): Scheme {
      return DEFAULT_SCHEME
    }

    /** Returns the MAJOR.MINOR.MICRO.PATCH-QUALIFIER scheme. */
    public fun withPatchNumber(): Scheme {
      return PATCH_SCHEME
    }

    public fun parse(versionString: String?): VersionNumber {
      return DEFAULT_SCHEME.parse(versionString)
    }
  }
}
