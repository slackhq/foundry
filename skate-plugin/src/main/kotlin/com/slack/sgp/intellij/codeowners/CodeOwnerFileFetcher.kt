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

import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.slack.sgp.intellij.SkatePluginSettings
import java.io.File
import java.nio.file.Path

/**
 * CodeOwnerFileFetcher is the interface for fetching the code owner yaml file. It's structured like
 * this so that we can provide test/fake instances for use in unit tests.
 */
interface CodeOwnerFileFetcher {
  fun getCodeOwnershipFile(): File?
}

class CodeOwnerFileFetcherImpl(
  private val project: Project,
  private val basePath: String = project.basePath ?: "",
) : CodeOwnerFileFetcher {

  private val logger = logger<CodeOwnerFileFetcherImpl>()

  override fun getCodeOwnershipFile(): File? {
    // get file path from settings if it's present
    val settings = project.service<SkatePluginSettings>()
    val filePathSetting = settings.codeOwnerFilePath
    // build full path and find file if file path setting is present
    return filePathSetting?.let {
      val fs = LocalFileSystem.getInstance()
      val path = Path.of(basePath, filePathSetting)
      logger.debug("getCodeOwnershipFile path location: $path")
      fs.findFileByNioFile(path)?.toNioPath()?.toFile()
    }
  }
}

class FakeCodeOwnerFileFetcherImpl(private val path: String) : CodeOwnerFileFetcher {
  override fun getCodeOwnershipFile(): File? {
    val fs = LocalFileSystem.getInstance()
    return fs.findFileByNioFile(Path.of(path))?.toNioPath()?.toFile()
  }
}
