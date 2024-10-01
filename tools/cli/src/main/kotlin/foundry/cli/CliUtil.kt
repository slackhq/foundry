/*
 * Copyright (C) 2022 Slack Technologies, LLC
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
@file:Suppress("unused")

package foundry.cli

import com.github.ajalt.clikt.core.MissingOption
import com.github.ajalt.clikt.parameters.options.NullableOption
import com.github.ajalt.clikt.parameters.options.OptionWithValues
import com.github.ajalt.clikt.parameters.options.transformAll
import java.io.File
import java.nio.file.FileVisitResult
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.FileVisitorBuilder
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.visitFileTree

/**
 * Skips `build` and cache directories (starting with `.`, like `.gradle`) in
 * [FileTreeWalks][FileTreeWalk].
 */
public fun FileTreeWalk.skipBuildAndCacheDirs(): FileTreeWalk {
  return onEnter { dir -> !dir.name.startsWith(".") && dir.name != "build" }
}

/**
 * Skips `build` and cache directories (starting with `.`, like `.gradle`) in
 * [FileTreeWalks][FileTreeWalk].
 */
@ExperimentalPathApi
public fun FileVisitorBuilder.skipBuildAndCacheDirs() {
  return onPreVisitDirectory { dir, _ ->
    if (dir.name.startsWith(".") || dir.name == "build") {
      FileVisitResult.SKIP_SUBTREE
    } else {
      FileVisitResult.CONTINUE
    }
  }
}

@OptIn(ExperimentalPathApi::class)
public fun Path.walkEachFile(
  maxDepth: Int = Int.MAX_VALUE,
  followLinks: Boolean = false,
  builderAction: FileVisitorBuilder.() -> Unit = {},
): Sequence<Path> {
  val files = mutableListOf<Path>()
  visitFileTree(maxDepth, followLinks) {
    builderAction()
    onVisitFile { file, _ ->
      files.add(file)
      FileVisitResult.CONTINUE
    }
  }
  return files.asSequence()
}

/** Filters by a specific [extension]. */
public fun Sequence<File>.filterByExtension(extension: String): Sequence<File> {
  return filter { it.extension == extension }
}

/** Filters by a specific [name]. */
public fun Sequence<File>.filterByName(
  name: String,
  withoutExtension: Boolean = true,
): Sequence<File> {
  return if (withoutExtension) {
    filter { it.nameWithoutExtension == name }
  } else {
    filter { it.name == name }
  }
}

/** Filters by a specific [extension]. */
@JvmName("filterByExtensionPath")
public fun Sequence<Path>.filterByExtension(extension: String): Sequence<Path> {
  return filter { it.extension == extension }
}

/** Filters by a specific [name]. */
@JvmName("filterByNamePath")
public fun Sequence<Path>.filterByName(
  name: String,
  withoutExtension: Boolean = true,
): Sequence<Path> {
  return if (withoutExtension) {
    filter { it.nameWithoutExtension == name }
  } else {
    filter { it.name == name }
  }
}

public fun List<String>.cleanLineFormatting(): List<String> {
  val cleanedBlankLines = mutableListOf<String>()
  var blankLineCount = 0
  for (newLine in this) {
    if (newLine.isBlank()) {
      if (blankLineCount == 1) {
        // Skip this line
      } else {
        blankLineCount++
        cleanedBlankLines += newLine
      }
    } else {
      blankLineCount = 0
      cleanedBlankLines += newLine
    }
  }

  return cleanedBlankLines.padNewline()
}

private fun List<String>.padNewline(): List<String> {
  val noEmpties = dropLastWhile { it.isBlank() }
  return noEmpties + ""
}

/**
 * Make the option return a set of calls; each item in the set is the value of one call.
 *
 * If the option is never called, the set will be empty. This must be applied after all other
 * transforms.
 *
 * ### Example:
 * ```
 * val opt: Set<Pair<Int, Int>> by option().int().pair().multipleSet()
 * ```
 *
 * @param default The value to use if the option is not supplied. Defaults to an empty set.
 * @param required If true, [default] is ignored and [MissingOption] will be thrown if no instances
 *   of the option are present on the command line.
 */
public fun <EachT, ValueT> NullableOption<EachT, ValueT>.multipleSet(
  default: Set<EachT> = emptySet(),
  required: Boolean = false,
): OptionWithValues<Set<EachT>, EachT, ValueT> {
  return transformAll(showAsRequired = required) {
    when {
      it.isEmpty() && required -> throw MissingOption(option)
      it.isEmpty() && !required -> default
      else -> it
    }.toSet()
  }
}
