//package com.slack.sgp.intellij.projectgen
//
//import androidx.compose.runtime.getValue
//import androidx.compose.runtime.mutableStateOf
//import androidx.compose.runtime.remember
//import androidx.compose.runtime.setValue
//import androidx.compose.ui.Alignment
//import androidx.compose.ui.awt.ComposePanel
//import androidx.compose.ui.input.key.Key
//import androidx.compose.ui.input.key.KeyEventType
//import androidx.compose.ui.input.key.isMetaPressed
//import androidx.compose.ui.input.key.key
//import androidx.compose.ui.input.key.type
//import androidx.compose.ui.res.painterResource
//import androidx.compose.ui.unit.dp
//import androidx.compose.ui.window.MenuBar
//import androidx.compose.ui.window.Window
//import androidx.compose.ui.window.WindowPosition
//import androidx.compose.ui.window.application
//import androidx.compose.ui.window.rememberWindowState
//import com.slack.circuit.foundation.Circuit
//import com.slack.circuit.foundation.CircuitContent
//import com.slack.circuit.runtime.ui.ui
//import java.nio.file.FileSystems
//import java.nio.file.Paths
//
//fun main(args: Array<String>) = application {
//  val rootDir = remember {
//    val path =
//      args.firstOrNull()
//        ?: FileSystems.getDefault().getPath(".").toAbsolutePath().normalize().toFile().absolutePath
//    check(Paths.get(path).toFile().isDirectory) { "Must pass a valid directory" }
//    path
//  }
//
//  val circuit = remember {
//    Circuit.Builder()
//      .addPresenterFactory { _, _, _ -> ProjectGenPresenter(rootDir) }
//      .addUiFactory { _, _ ->
//        ui<ProjectGenScreen.State> { state, modifier -> ProjectGen(state, modifier) }
//      }
//      .build()
//  }
//
//  var darkMode by remember { mutableStateOf(false) }
//
//  ComposePanel().apply {
//    SlackDesktopTheme(useDarkMode = darkMode) {
////      CircuitContent(ProjectGenScreen, circuit = circuit)
//    }
//  }
//
//
//  Window(
//    onCloseRequest = ::exitApplication,
//    title = "Project Generator",
//    icon = painterResource("icon.png"),
//    onPreviewKeyEvent = { keyEvent ->
//      when {
//        // ⌘ + W
//        keyEvent.key == Key.W &&
//          keyEvent.isMetaPressed &&
//          keyEvent.type == KeyEventType.KeyDown -> {
//          exitApplication()
//          true
//        }
//        // ⌘ + U
//        // Toggles dark mode
//        keyEvent.key == Key.U &&
//          keyEvent.isMetaPressed &&
//          keyEvent.type == KeyEventType.KeyDown -> {
//          darkMode = !darkMode
//          true
//        }
//        else -> false
//      }
//    },
//    state =
//      rememberWindowState(
//        width = 600.dp,
//        height = 800.dp,
//        position = WindowPosition(Alignment.Center),
//      ),
//  ) {
//    MenuBar {
//      Menu(text = "UI", mnemonic = 'U') {
//        Item(
//          text =
//            if (darkMode) {
//              "Switch to light mode"
//            } else {
//              "Switch to dark mode"
//            },
//          onClick = { darkMode = !darkMode },
//        )
//      }
//    }
//
//    SlackDesktopTheme(useDarkMode = darkMode) {
//      CircuitContent(ProjectGenScreen, circuit = circuit)
//    }
//  }
//}
