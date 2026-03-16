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
import com.android.build.api.dsl.KotlinMultiplatformAndroidHostTest
import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryExtension
import com.android.build.api.dsl.LibraryAndroidResources
import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.dsl.TestExtension
import com.android.build.api.dsl.TestOptions
import org.gradle.api.Action

/**
 * Abstraction API over [CommonExtension] due to AGP 9 having an annoying disparate API between it
 * and the [KotlinMultiplatformAndroidLibraryExtension].
 */
public sealed interface CommonExtensionHandler {
  public val buildFeatures: BuildFeatures?

  public fun withAndroidUnitTest(action: Action<AndroidUnitTestOptions>)

  /**
   * Simple abstraction over unit/host test options, because Android KMP uses different naming/APIs
   * for this :(.
   */
  public sealed interface AndroidUnitTestOptions {

    public var includeAndroidResources: Boolean

    public class Default(private val delegate: TestOptions) : AndroidUnitTestOptions {
      override var includeAndroidResources: Boolean
        get() = delegate.unitTests.isIncludeAndroidResources
        set(value) {
          delegate.unitTests.isIncludeAndroidResources = value
        }
    }

    public class Kmp(private val delegate: KotlinMultiplatformAndroidHostTest) :
      AndroidUnitTestOptions {
      override var includeAndroidResources: Boolean
        get() = delegate.isIncludeAndroidResources
        set(value) {
          delegate.isIncludeAndroidResources = value
        }
    }
  }

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

      override fun withAndroidUnitTest(action: Action<AndroidUnitTestOptions>) {
        action.execute(AndroidUnitTestOptions.Default(delegate.testOptions))
      }
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

      override fun withAndroidUnitTest(action: Action<AndroidUnitTestOptions>) {
        delegate.withHostTest { action.execute(AndroidUnitTestOptions.Kmp(this)) }
      }
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
