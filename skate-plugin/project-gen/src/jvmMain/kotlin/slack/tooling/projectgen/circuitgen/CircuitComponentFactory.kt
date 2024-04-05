package slack.tooling.projectgen.circuitgen

class CircuitComponentFactory {

  fun generateCircuitComponents(directory: String, packageName: String?, className: String, components: List<CircuitComponent>) {
    if (directory.isBlank() || className.isBlank()) {
      throw IllegalArgumentException("Directory or class name cannot be blank")
    }

    components.forEach { component ->
      val baseDirectory = if (component.isTestComponent()) {
        directory.replace("src/main", "src/test")
      } else {
        directory
      }
      component.writeToFile(baseDirectory, packageName, className)
    }
  }
  fun generateCircuitAndComposeUI(directory: String, packageName: String?, className: String) {
    val components = listOf(
      CircuitScreen(),
      CircuitPresenter(),
      CircuitUiFeature(),
      CircuitPresenterTest(),
      CircuitUiTest()
    )
    generateCircuitComponents(directory, packageName, className, components)
  }

  fun generateCircuitPresenter(directory: String, packageName: String?, className: String) {
    val components = listOf(
      CircuitScreen(),
      CircuitPresenter(),
      CircuitPresenterTest()
    )
    generateCircuitComponents(directory, packageName, className, components)
  }

  fun generateUdfViewModel(directory: String, packageName: String?, className: String) {
    val components = listOf(
      CircuitScreen(),
      CircuitViewModel()
    )
    generateCircuitComponents(directory, packageName, className, components)
  }
}
