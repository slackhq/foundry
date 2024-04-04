package slack.tooling.projectgen

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CircuitComponentTest {
  @Test
  fun testGenerateCircuitComponentWithNullPackage() {
    val component = FakeCircuitComponent(false)
    val result = component.generate(null, "Foo")
    val expectedContent = "\nclass FooFake"
    assertThat(result).isEqualTo(expectedContent)
  }

  @Test
  fun testGenerateCircuitComponentWithPackage() {
    val component = FakeCircuitComponent(false)
    val result = component.generate("com.example.feature", "Foo")
    val expectedContent = """
      package com.example.feature
      
      class FooFake
      """.trimIndent()
    assertThat(result).isEqualTo(expectedContent)
  }
}
