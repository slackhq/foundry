package foundry.gradle.android

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.BuildFeatures
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryExtension
import com.android.build.api.dsl.LibraryAndroidResources
import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.dsl.TestExtension
import com.android.build.api.dsl.TestOptions

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
    internal class DefaultImpl(private val delegate: CommonExtension) : Default {
      override val buildFeatures: BuildFeatures
        get() = delegate.buildFeatures

      override val testOptions: TestOptions
        get() = delegate.testOptions
    }

    internal class LibraryImpl(private val delegate: LibraryExtension) :
      Library, CommonExtensionHandler by DefaultImpl(delegate) {
      override var resourcePrefix: String?
        get() = delegate.resourcePrefix
        set(value) {
          delegate.resourcePrefix = value
        }

      override val androidResources: LibraryAndroidResources
        get() = delegate.androidResources
    }

    internal class KmpLibraryImpl(
      private val delegate: KotlinMultiplatformAndroidLibraryExtension
    ) : KmpLibrary {
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
