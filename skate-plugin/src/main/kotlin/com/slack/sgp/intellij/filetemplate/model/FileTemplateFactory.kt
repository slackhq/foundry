package com.slack.sgp.intellij.filetemplate.model

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.charleskorn.kaml.decodeFromStream
import java.io.InputStream


class FileTemplateFactory(inputStream: InputStream) {
    private var templates: Map<String, TemplateSetting>? = null

    init {
      parseFileTemplateFromSettingFile(inputStream)
    }
    private fun parseFileTemplateFromSettingFile(inputStream: InputStream) {
      val table = Yaml(configuration = YamlConfiguration(strictMode = false)).decodeFromStream<FileTemplateSettings>(inputStream)
      templates = table.templates.associateBy { it.name }
    }

    fun getTemplates(): Map<String, TemplateSetting> {
      return templates ?: throw IllegalStateException("Templates not loaded properly")
    }
}