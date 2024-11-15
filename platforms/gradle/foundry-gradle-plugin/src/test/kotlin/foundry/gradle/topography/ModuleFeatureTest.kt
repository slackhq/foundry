package foundry.gradle.topography

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ModuleFeatureTest {
  @Test
  fun overridesTest() {
    val customExplanation = "This is a custom explanation"
    val config = ModuleFeaturesConfig(
      _defaultFeatureOverrides = listOf(
        mapOf(
          "name" to DefaultFeatures.Dagger.name,
          "explanation" to customExplanation
        )
      )
    )

    val overriddenExplanation = config.loadFeatures().getValue(DefaultFeatures.Dagger.name).explanation
    assertThat(overriddenExplanation).isEqualTo(customExplanation)
  }
}