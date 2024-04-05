package slack.tooling.projectgen

import org.junit.Test
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import slack.tooling.projectgen.circuitgen.CircuitComponentFactory
import slack.tooling.projectgen.circuitgen.FakeCircuitComponent

class CircuitComponentFactoryTest {
  @Test
  fun testGenerateCircuitComponents() {
    val screen = FakeCircuitComponent(false)
    val presenter = FakeCircuitComponent(false)
    val presenterTest = FakeCircuitComponent(true)
    val uiTest = FakeCircuitComponent(true)

    val components = listOf(screen, presenter, presenterTest, uiTest)
    val directory = "users/repo/src/main/kotlin/com/feature/message"
    val packageName = "com.feature.message"
    CircuitComponentFactory().generateCircuitComponents(directory, packageName, "Foo", components)

    assertThat(screen.directoryCreated).isEqualTo(directory)
    assertThat(presenter.directoryCreated).isEqualTo(directory)
    assertThat(presenterTest.directoryCreated).isEqualTo(directory.replace("src/main", "src/test"))
    assertThat(uiTest.directoryCreated).isEqualTo(directory.replace("src/main", "src/test"))
  }

  @Test
  fun testGenerateComponentsThrowExceptionOnEmptyInput() {
    val screen = FakeCircuitComponent(false)
    val presenter = FakeCircuitComponent(false)
    val components = listOf(screen, presenter)
    assertThrows(IllegalArgumentException::class.java) {
      CircuitComponentFactory().generateCircuitComponents("", "", "Foo", components)
    }
  }
}
