package foundry.common.versioning

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VersionNumberTest {

  @Test
  fun `test creation of VersionNumber with major, minor and micro`() {
    val version = VersionNumber(1, 0, 0, null)
    assertThat(version.major).isEqualTo(1)
    assertThat(version.minor).isEqualTo(0)
    assertThat(version.micro).isEqualTo(0)
    assertThat(version.patch).isEqualTo(0)
    assertThat(version.qualifier).isNull()
  }

  @Test
  fun `test creation of VersionNumber with major, minor, micro, and patch`() {
    val version = VersionNumber(1, 0, 0, 1, "beta")
    assertThat(version.major).isEqualTo(1)
    assertThat(version.minor).isEqualTo(0)
    assertThat(version.micro).isEqualTo(0)
    assertThat(version.patch).isEqualTo(1)
    assertThat(version.qualifier).isEqualTo("beta")
  }

  @Test
  fun `test equals for identical VersionNumbers`() {
    val version1 = VersionNumber(1, 0, 0, 1, null)
    val version2 = VersionNumber(1, 0, 0, 1, null)
    assertThat(version1).isEqualTo(version2)
  }

  @Test
  fun `test equals for different VersionNumbers`() {
    val version1 = VersionNumber(1, 0, 0, 1, null)
    val version2 = VersionNumber(2, 0, 0, 1, null)
    assertThat(version1).isNotEqualTo(version2)
  }

  @Test
  fun `test compareTo for different major versions`() {
    val version1 = VersionNumber(1, 0, 0, 0, null)
    val version2 = VersionNumber(2, 0, 0, 0, null)
    assertThat(version1.compareTo(version2)).isLessThan(0)
  }

  @Test
  fun `test compareTo for same major but different minor versions`() {
    val version1 = VersionNumber(1, 1, 0, 0, null)
    val version2 = VersionNumber(1, 2, 0, 0, null)
    assertThat(version1.compareTo(version2)).isLessThan(0)
  }

  @Test
  fun `test compareTo for same major and minor but different micro versions`() {
    val version1 = VersionNumber(1, 1, 1, 0, null)
    val version2 = VersionNumber(1, 1, 2, 0, null)
    assertThat(version1.compareTo(version2)).isLessThan(0)
  }

  @Test
  fun `test compareTo for versions with patch`() {
    val version1 = VersionNumber(1, 1, 1, 1, null)
    val version2 = VersionNumber(1, 1, 1, 2, null)
    assertThat(version1.compareTo(version2)).isLessThan(0)
  }

  @Test
  fun `test compareTo for versions with qualifier string`() {
    val version1 = VersionNumber(1, 1, 1, 0, "alpha")
    val version2 = VersionNumber(1, 1, 1, 0, "beta")
    assertThat(version1.compareTo(version2)).isLessThan(0)
  }

  @Test
  fun `test toString for default formatting`() {
    val version = VersionNumber(1, 1, 1, null)
    assertThat(version.toString()).isEqualTo("1.1.1")
  }

  @Test
  fun `test toString for patch formatting`() {
    val version = VersionNumber(1, 1, 1, 1, "rc")
    assertThat(version.toString()).isEqualTo("1.1.1.1-rc")
  }

  @Test
  fun `test parsing a valid version string`() {
    val version = VersionNumber.parse("1.2.3-alpha")
    assertThat(version.major).isEqualTo(1)
    assertThat(version.minor).isEqualTo(2)
    assertThat(version.micro).isEqualTo(3)
    assertThat(version.patch).isEqualTo(0)
    assertThat(version.qualifier).isEqualTo("alpha")
  }

  @Test
  fun `test parsing a version string with patch`() {
    val version = VersionNumber.withPatchNumber().parse("1.2.3.4-beta")
    assertThat(version.major).isEqualTo(1)
    assertThat(version.minor).isEqualTo(2)
    assertThat(version.micro).isEqualTo(3)
    assertThat(version.patch).isEqualTo(4)
    assertThat(version.qualifier).isEqualTo("beta")
  }

  @Test
  fun `test baseVersion excludes qualifier`() {
    val version = VersionNumber(1, 1, 1, 0, "beta")
    val baseVersion = version.baseVersion
    assertThat(baseVersion.major).isEqualTo(1)
    assertThat(baseVersion.minor).isEqualTo(1)
    assertThat(baseVersion.micro).isEqualTo(1)
    assertThat(baseVersion.patch).isEqualTo(0)
    assertThat(baseVersion.qualifier).isNull()
  }

  @Test
  fun `test comparator for sorting VersionNumbers`() {
    val versions =
      listOf(
        VersionNumber(2, 0, 0, 0, null),
        VersionNumber(1, 2, 0, 0, null),
        VersionNumber(1, 1, 1, 0, "alpha"),
        VersionNumber(1, 1, 1, 0, "beta"),
      )
    val sortedVersions = versions.sorted()
    assertThat(sortedVersions)
      .containsExactly(
        VersionNumber(1, 1, 1, 0, "alpha"),
        VersionNumber(1, 1, 1, 0, "beta"),
        VersionNumber(1, 2, 0, 0, null),
        VersionNumber(2, 0, 0, 0, null),
      )
      .inOrder()
  }
}
