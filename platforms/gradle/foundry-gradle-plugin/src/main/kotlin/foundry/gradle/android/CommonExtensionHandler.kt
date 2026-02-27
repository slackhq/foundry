/*
 * Copyright (C) 2026 Slack Technologies, LLC
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
package foundry.gradle.android

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.BuildFeatures
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryExtension
import com.android.build.api.dsl.LibraryAndroidResources
import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.dsl.TestExtension
import com.android.build.api.dsl.TestOptions

/**
 * Abstraction API over [CommonExtension] due to AGP 9 having an annoying disparate API between it
 * and the [KotlinMultiplatformAndroidLibraryExtension].
 */
public sealed interface CommonExtensionHandler {
  public val buildFeatures: BuildFeatures?
  public val testOptions: TestOptions?

  public interface Default : CommonExtensionHandler

  public interface LibraryAndroidResourcesHolder {
    public val androidResources: LibraryAndroidResources
  }

  public interface Library : CommonExtensionHandler, LibraryAndroidResourcesHolder {
    // Library only
    public var resourcePrefix: String?
  }

  public interface KmpLibrary : CommonExtensionHandler, LibraryAndroidResourcesHolder

  public companion object {
    private class DefaultImpl(private val delegate: CommonExtension) : Default {
      override val buildFeatures: BuildFeatures
        get() = delegate.buildFeatures

      override val testOptions: TestOptions
        get() = delegate.testOptions
    }

    private class LibraryImpl(private val delegate: LibraryExtension) :
      Library, CommonExtensionHandler by DefaultImpl(delegate) {
      override var resourcePrefix: String?
        get() = delegate.resourcePrefix
        set(value) {
          delegate.resourcePrefix = value
        }

      override val androidResources: LibraryAndroidResources
        get() = delegate.androidResources
    }

    private class KmpLibraryImpl(private val delegate: KotlinMultiplatformAndroidLibraryExtension) :
      KmpLibrary {
      override val androidResources: LibraryAndroidResources
        get() = delegate.androidResources

      override val buildFeatures: BuildFeatures? = null
      override val testOptions: TestOptions? = null
    }

    internal operator fun invoke(extension: ApplicationExtension): CommonExtensionHandler {
      return DefaultImpl(extension)
    }

    internal operator fun invoke(extension: LibraryExtension): CommonExtensionHandler {
      return LibraryImpl(extension)
    }

    internal operator fun invoke(extension: TestExtension): CommonExtensionHandler {
      return DefaultImpl(extension)
    }

    internal operator fun invoke(
      extension: KotlinMultiplatformAndroidLibraryExtension
    ): CommonExtensionHandler {
      return KmpLibraryImpl(extension)
    }
  }
}
