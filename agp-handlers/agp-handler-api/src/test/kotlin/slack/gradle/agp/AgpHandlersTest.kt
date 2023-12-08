package slack.gradle.agp

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AgpHandlersTest {
  @Test
  fun parseTest() {
    assertVersion("8.0.0-alpha01")
    assertVersion("8.0.0-beta01")
    assertVersion("8.0.0-beta11")
    assertVersion("8.0.0-rc11")
    assertVersion("8.0.0-dev")
  }

  private fun assertVersion(version: String) {
    val versionToCheck =
      if (version[version.lastIndex - 1] == '0') {
        // AGP doesn't print the leading zero in their versions here
        version.substring(0, version.lastIndex - 1) + version[version.lastIndex]
      } else {
        version
      }
    assertThat(computeAndroidPluginVersion(version).toString())
      .isEqualTo("Android Gradle Plugin version $versionToCheck")
  }
}
