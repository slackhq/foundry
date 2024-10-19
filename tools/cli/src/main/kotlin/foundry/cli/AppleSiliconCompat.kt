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
package foundry.cli

import eu.jrie.jetbrains.kotlinshell.shell.shell
import foundry.cli.AppleSiliconCompat.Arch.ARM64
import foundry.cli.AppleSiliconCompat.Arch.X86_64
import okio.Buffer

public object AppleSiliconCompat {
  /**
   * Validates that the current process is not running under Rosetta.
   *
   * If the current process is running under Rosetta, it (Java in this case) will think it's running
   * x86 but Rosetta leaves a peephole to check if the current process is running as a translated
   * binary.
   *
   * We do this to ensure that folks are using arm64 JDK builds for native performance.
   *
   * Peephole:
   * https://developer.apple.com/documentation/apple-silicon/about-the-rosetta-translation-environment#Determine-Whether-Your-App-Is-Running-as-a-Translated-Binary
   */
  @Suppress("ReturnCount")
  public fun validate(errorMessage: () -> String) {
    if (System.getenv("FOUNDRY_SKIP_APPLE_SILICON_CHECK")?.toBoolean() == true) {
      // Toe-hold to skip this check if anything goes wrong.
      return
    }

    if (!isMacOS()) {
      // Not a macOS device, move on!
      return
    }

    val arch = Arch.get()
    if (arch == ARM64) {
      // Already running on an arm64 JDK, we're good
      return
    }

    check(arch == X86_64) { "Unsupported architecture: $arch" }

    shell {
      val buffer = Buffer()
      val pipeline = pipeline {
        "sysctl -in sysctl.proc_translated".process() pipe buffer.outputStream()
      }
      pipeline.join()
      val isTranslated = buffer.readUtf8().trim()
      when {
        isTranslated.isEmpty() -> {
          // True x86 device! Move on
        }
        isTranslated == "1" -> {
          error(errorMessage())
        }
        isTranslated != "0" -> {
          @Suppress("MaxLineLength") // It's a string, Detekt. A STRING
          error(
            "Could not determine if Rosetta is running (translated value was '$isTranslated'). Please ensure that sysctl is available on your PATH env. It is normally available under /usr/sbin or /sbin."
          )
        }
      }
    }
  }

  public fun isMacOS(): Boolean = System.getProperty("os.name") == "Mac OS X"

  public enum class Arch {
    X86_64,
    ARM64;

    public companion object {
      // Null indicates unknown arch. Not necessarily bad, but we also don't have anything to say
      // about it.
      private val CURRENT by lazy { from(System.getProperty("os.arch")) }

      public fun get(): Arch? = CURRENT

      private fun from(arch: String) =
        when (arch) {
          "x86_64" -> X86_64
          "aarch64" -> ARM64
          else -> null
        }
    }
  }
}
