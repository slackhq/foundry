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
package slack.tooling.projectgen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.slack.circuit.runtime.presenter.Presenter
import java.io.File
import slack.tooling.projectgen.*

internal class ProjectGenPresenter(
  private val rootDir: String,
  private val onDismissDialog: () -> Unit,
  private val onSync: () -> Unit,
) : Presenter<ProjectGenScreen.State> {
  private val path =
    TextElement(
      "",
      "Gradle Path",
      description = "The Gradle-style project path (e.g. ':emoji')",
      prefixTransformation = ":",
      validationRegex = Regex("[a-zA-Z]([A-Za-z0-9\\-_:.])+"),
    )

  private val packageName =
    TextElement(
      "",
      "Package Name",
      description =
        "The project package name (must start with 'slack.') This is used for both source packages and android.namespace.",
      prefixTransformation = "slack.",
      validationRegex = Regex("[A-Za-z0-9.]+"),
    )

  private val android =
    CheckboxElement(
      false,
      name = "Android",
      hint = "This project is an Android library project (default is JVM only).",
    )
  private val androidViewBinding =
    CheckboxElement(false, name = "ViewBinding", hint = "Enables ViewBinding.", indentLevel = 1)

  private val androidResources =
    CheckboxElement(false, name = "Resources", hint = "Enables android resources.", indentLevel = 1)

  private val androidResourcePrefix =
    TextElement(
      "",
      "Resource prefix (required)",
      description =
        "Android library projects that enable resources should use a resource prefix to avoid resource merging collisions. Examples include 'sk_', 'emoji_', 'slack_', etc.",
      indentLevel = 3,
      initialVisibility = false,
      dependentElements = listOf(androidResources),
    )

  private val robolectric =
    CheckboxElement(
      false,
      name = "Robolectric",
      hint = "Enables Robolectric for unit tests.",
      indentLevel = 1,
    )

  private val androidTest =
    CheckboxElement(
      false,
      name = "Android Tests",
      hint = "Enables instrumentation tests.",
      indentLevel = 1,
    )

  private val dagger = CheckboxElement(false, name = "Dagger", hint = "Enables Dagger and Anvil.")

  private val daggerRuntimeOnly =
    CheckboxElement(
      false,
      name = "Runtime Only",
      hint = "Only add the Dagger runtime dependencies, no code gen.",
      indentLevel = 1,
    )

  private val circuit = CheckboxElement(false, name = "Circuit", hint = "Enables Circuit")

  private val compose = CheckboxElement(false, name = "Compose", hint = "Enables Jetpack Compose.")

  private val uiElements =
    mutableStateListOf(
      SectionElement("Path Details", "(Required)"),
      TextElement(rootDir, "Project root dir", readOnly = true),
      path,
      packageName,
      DividerElement,
      SectionElement("Features", "(Select all that apply)"),
      android,
      androidResources,
      androidResourcePrefix,
      androidViewBinding,
      robolectric,
      androidTest,
      dagger,
      daggerRuntimeOnly,
      compose,
      circuit,
    )

  @Composable
  override fun present(): ProjectGenScreen.State {
    // Wire UI element dependencies
    androidViewBinding.isVisible = android.isChecked
    androidResources.isVisible = android.isChecked
    androidResourcePrefix.isVisible = androidResources.isChecked
    robolectric.isVisible = android.isChecked
    androidTest.isVisible = android.isChecked
    daggerRuntimeOnly.isVisible = dagger.isChecked

    var showDoneDialog by remember { mutableStateOf(false) }
    var showErrorDialog by remember { mutableStateOf(false) }

    return ProjectGenScreen.State(
      uiElements = uiElements,
      showDoneDialog = showDoneDialog,
      showErrorDialog = showErrorDialog,
      canGenerate = path.isValid && packageName.isValid,
    ) { event ->
      when (event) {
        ProjectGenScreen.Event.Quit -> onDismissDialog()
        ProjectGenScreen.Event.Sync -> onSync()
        ProjectGenScreen.Event.Reset -> {
          showDoneDialog = false
          showErrorDialog = false
          resetElements()
        }
        ProjectGenScreen.Event.Generate -> {
          if (generate()) {
            showDoneDialog = true
          } else {
            showDoneDialog = false
            showErrorDialog = true
          }
        }
      }
    }
  }

  private fun resetElements() {
    path.reset()
    packageName.reset()
    android.reset()
    androidResourcePrefix.reset()
    androidViewBinding.reset()
    androidResources.reset()
    dagger.reset()
    daggerRuntimeOnly.reset()
    robolectric.reset()
    compose.reset()
    circuit.reset()
  }

  private fun generate(): Boolean {
    return generate(
      rootDir = File(rootDir),
      path = ":${path.value}",
      packageName = "slack.${packageName.value}",
      android = android.isChecked,
      androidFeatures =
        buildSet {
          if (androidViewBinding.isChecked) {
            add("view-binding")
          }
        },
      androidResourcePrefix = androidResourcePrefix.value.takeIf { androidResources.isChecked },
      dagger = dagger.isChecked,
      daggerFeatures =
        buildSet {
          if (daggerRuntimeOnly.isChecked) {
            add("runtime-only")
          }
        },
      robolectric = robolectric.isChecked,
      compose = compose.isChecked,
      androidTest = androidTest.isChecked,
      circuit = circuit.isChecked,
    )
  }

  @Suppress("LongParameterList")
  private fun generate(
    rootDir: File,
    path: String,
    packageName: String,
    android: Boolean,
    androidFeatures: Set<String>,
    androidResourcePrefix: String?,
    dagger: Boolean,
    daggerFeatures: Set<String>,
    robolectric: Boolean,
    compose: Boolean,
    androidTest: Boolean,
    circuit: Boolean,
  ): Boolean {
    val features = mutableListOf<Feature>()
    val androidLibraryEnabled =
      android && (androidFeatures.isNotEmpty() || androidTest || androidResourcePrefix != null)
    if (androidLibraryEnabled) {
      features +=
        AndroidLibraryFeature(
          androidResourcePrefix,
          viewBindingEnabled = "view-binding" in androidFeatures,
          androidTest = androidTest,
          packageName = packageName,
        )
    }

    features += KotlinFeature(packageName, isAndroid = android)

    if (dagger) {
      features += DaggerFeature(runtimeOnly = "runtime-only" in daggerFeatures)
    }

    if (compose) {
      features += ComposeFeature
    }

    if (robolectric) {
      features += RobolectricFeature
    }

    if (circuit) {
      features += CircuitFeature
    }

    val buildFile = BuildFile(emptyList())
    val readMeFile = ReadMeFile()

    val project = Project(path, buildFile, readMeFile, features)
    return if (project.checkValidPath(rootDir)) {
      project.writeTo(rootDir)
      true
    } else {
      false
    }
  }
}
