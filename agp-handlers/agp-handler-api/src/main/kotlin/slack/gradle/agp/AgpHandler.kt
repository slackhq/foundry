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
package slack.gradle.agp

import com.android.build.api.dsl.CommonExtension
import com.android.build.gradle.internal.dsl.TestOptions
import org.gradle.api.tasks.testing.Test

/** An interface for handling different AGP versions via (mostly) version-agnostic APIs. */
public interface AgpHandler {

  /** The current AGP version. */
  public val agpVersion: String

  /** Shim for `testOptions.unitTest.all`, which had a signature change in AGP 8.x. */
  public fun allUnitTestOptions(options: TestOptions.UnitTestOptions, body: (Test) -> Unit)

  /**
   * Shim for packagingOptions, which had a signature change in AGP 8.x from `PackagingOptions` to
   * `Packaging`.
   */
  public fun packagingOptions(
    commonExtension: CommonExtension<*, *, *, *>,
    resourceExclusions: Collection<String>,
    jniPickFirsts: Collection<String>,
  )
}

/**
 * Raw AGP [VersionNumber], which can include qualifiers like `-beta03`. Not usually what you want
 * in comparisons.
 */
internal val AgpHandler.agpVersionNumberRaw: VersionNumber
  get() = VersionNumber.parse(agpVersion)

/**
 * Base AGP [VersionNumber], which won't include qualifiers like `-beta03`. Usually what you want in
 * comparisons.
 */
public val AgpHandler.agpVersionNumber: VersionNumber
  get() = agpVersionNumberRaw.baseVersion
