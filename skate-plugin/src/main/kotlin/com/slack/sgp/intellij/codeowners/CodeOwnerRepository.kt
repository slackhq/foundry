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

import com.intellij.openapi.diagnostic.logger
import java.io.FileNotFoundException

/**
 * CodeOwnerRepository is responsible for reading and caching the code owners info from the csv
 * specific via CodeOwnerFileHelper.getCodeOwnershipCsv(project). Invalid file formats or rows will
 * be ignored.
 */
class CodeOwnerRepository(codeOwnerFileFetcher: CodeOwnerFileFetcher) {

  private var codeOwnershipMap: Map<String, CodeOwnerInfo> = emptyMap()
  private val logger = logger<CodeOwnerRepository>()

  init {
    val codeOwnershipFile = codeOwnerFileFetcher.getCodeOwnershipCsv()
    if (codeOwnershipFile != null) {
      try {
        codeOwnershipMap =
          codeOwnershipFile
            .readLines()
            .asSequence()
            .mapIndexed { index, line -> parseCodeOwnerLineInfo(index, line) }
            .filterNotNull()
            .associateBy { codeOwnerInfo -> codeOwnerInfo.packagePattern }
      } catch (fileNotFoundException: FileNotFoundException) {
        logger.error(
          "FileNotFoundException caught parsing code ownership file",
          fileNotFoundException
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

  private fun parseCodeOwnerLineInfo(index: Int, line: String): CodeOwnerInfo? {
    val splitLine = line.split(",")
    return if (splitLine.size == 2) {
      CodeOwnerInfo(team = splitLine[1], packagePattern = splitLine[0], codeOwnerLineNumber = index)
    } else {
      null
    }
  }
}
