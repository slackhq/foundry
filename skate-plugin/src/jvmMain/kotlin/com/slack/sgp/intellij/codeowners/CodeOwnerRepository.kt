/*
 * Copyright (C) 2023 Slack Technologies, LLC
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
package com.slack.sgp.intellij.codeowners

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.intellij.openapi.diagnostic.logger
import com.slack.sgp.intellij.codeowners.model.CodeOwnersFile
import java.io.File
import java.io.FileNotFoundException
import kotlin.text.Charsets.UTF_8

/**
 * CodeOwnerRepository is responsible for reading and caching the code owners info from the yaml
 * specific via CodeOwnerFileHelper.getCodeOwnershipFile(project). Invalid file formats or rows will
 * be ignored.
 */
class CodeOwnerRepository(codeOwnerFileFetcher: CodeOwnerFileFetcher) {

  private var codeOwnershipMap: Map<String, CodeOwnerInfo> = emptyMap()
  private val logger = logger<CodeOwnerRepository>()

  init {
    val codeOwnershipFile = codeOwnerFileFetcher.getCodeOwnershipFile()
    if (codeOwnershipFile != null) {
      try {
        // Construct map of contents -> index (line number) for fast access later. This is needed
        // for looking up line numbers in the ownership file.
        val codeOwnershipLineMap =
          codeOwnershipFile
            .readLines()
            .mapIndexed { index, line -> line.trimStart(' ', '-') to index }
            .toMap()

        // Marshal yaml
        val ownershipFile = marshalCodeOwnershipYaml(codeOwnershipFile)

        // Work through ownershipFile.ownership list by creating CodeOwnerInfo objects, flattening
        // then and turning that into a map of packagePattern -> CodeOwnerInfo for fast access
        // later.
        codeOwnershipMap =
          ownershipFile.ownership
            .map { codeOwner ->
              codeOwner.paths.map { path ->
                // Lookup line in ownership file (0 if not found) and construct CodeOwnerInfo
                CodeOwnerInfo(codeOwner.name, path, codeOwnershipLineMap[path] ?: 0)
              }
            }
            .flatten()
            .associateBy { codeOwnerInfo -> codeOwnerInfo.packagePattern }
      } catch (fileNotFoundException: FileNotFoundException) {
        logger.error(
          "FileNotFoundException caught parsing code ownership file",
          fileNotFoundException,
        )
      }
      logger.debug("Code ownership lines read into codeOwnershipMap: ${codeOwnershipMap.keys.size}")
    }
  }

  fun getCodeOwnership(filePath: String): List<CodeOwnerInfo> {
    return codeOwnershipMap.keys
      .filter { filePattern -> Regex(pattern = filePattern).containsMatchIn(filePath) }
      .map { codeOwnershipMap[it]!! }
  }

  private fun marshalCodeOwnershipYaml(file: File): CodeOwnersFile {
    val fileContents = file.readText(charset = UTF_8)
    return Yaml(configuration = YamlConfiguration(strictMode = false))
      .decodeFromString(CodeOwnersFile.serializer(), fileContents)
  }
}
