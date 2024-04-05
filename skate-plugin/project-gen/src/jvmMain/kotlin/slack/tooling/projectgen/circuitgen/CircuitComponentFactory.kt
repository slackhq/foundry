/*
 * Copyright (C) 2024 Slack Technologies, LLC
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
package slack.tooling.projectgen.circuitgen

class CircuitComponentFactory {

  fun generateCircuitComponents(
    directory: String,
    packageName: String?,
    className: String,
    components: List<CircuitComponent>,
  ) {
    if (directory.isBlank() || className.isBlank()) {
      throw IllegalArgumentException("Directory or class name cannot be blank")
    }

    components.forEach { component ->
      val baseDirectory =
        if (component.isTestComponent()) {
          directory.replace("src/main", "src/test")
        } else {
          directory
        }
      component.writeToFile(baseDirectory, packageName, className)
    }
  }

  fun generateCircuitAndComposeUI(directory: String, packageName: String?, className: String) {
    val components =
      listOf(
        CircuitScreen(),
        CircuitPresenter(),
        CircuitUiFeature(),
        CircuitPresenterTest(),
        CircuitUiTest(),
      )
    generateCircuitComponents(directory, packageName, className, components)
  }

  fun generateCircuitPresenter(directory: String, packageName: String?, className: String) {
    val components = listOf(CircuitScreen(), CircuitPresenter(), CircuitPresenterTest())
    generateCircuitComponents(directory, packageName, className, components)
  }

  fun generateUdfViewModel(directory: String, packageName: String?, className: String) {
    val components = listOf(CircuitScreen(), CircuitViewModel())
    generateCircuitComponents(directory, packageName, className, components)
  }
}
