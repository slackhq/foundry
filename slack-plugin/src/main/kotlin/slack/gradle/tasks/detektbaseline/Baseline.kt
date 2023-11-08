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
package slack.gradle.tasks.detektbaseline

import java.io.BufferedWriter
import java.nio.file.Files
import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory
import org.apache.commons.text.StringEscapeUtils
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList

internal typealias FindingsIdList = Set<String>

internal fun Path.exists(): Boolean = Files.exists(this)

internal fun Path.isFile(): Boolean = Files.isRegularFile(this)

internal data class Baseline(
  val manuallySuppressedIssues: FindingsIdList,
  val currentIssues: FindingsIdList
) {

  fun writeTo(path: Path) {
    // There are fancy DOM writing APIs out there, but we don't need it here for this simple stuff.
    Files.newOutputStream(path).bufferedWriter().use { writer ->
      writer.apply {
        write("<?xml version=\"1.0\" ?>")
        newLine()
        write("<SmellBaseline>")
        newLine()
        manuallySuppressedIssues.writeTo(writer, "ManuallySuppressedIssues")
        newLine()
        currentIssues.writeTo(writer, "CurrentIssues")
        newLine()
        write("</SmellBaseline>")
        newLine()
      }
    }
  }

  companion object {
    fun load(baselineFile: Path): Baseline {
      require(baselineFile.exists()) { "Baseline file does not exist." }
      require(baselineFile.isFile()) { "Baseline file is not a regular file." }
      val builderFactory = DocumentBuilderFactory.newInstance()
      val docBuilder = builderFactory.newDocumentBuilder()
      val doc = docBuilder.parse(Files.newInputStream(baselineFile).buffered())
      val element = doc.documentElement
      val manuallySuppressed =
        element.getSingleElementByTag("ManuallySuppressedIssues").parseIdValues()
      val currentIssues = element.getSingleElementByTag("CurrentIssues").parseIdValues()
      return Baseline(manuallySuppressed, currentIssues)
    }
  }
}

private fun Element.getSingleElementByTag(tagName: String): Element {
  return getElementsByTagName(tagName).asSequence().filterIsInstance<Element>().single()
}

private fun Element.parseIdValues(): Set<String> {
  return getElementsByTagName("ID").asSequence().mapTo(LinkedHashSet(), Node::getTextContent)
}

private fun NodeList.asSequence(): Sequence<Node> {
  return (0 until length).asSequence().map(::item)
}

private fun Set<String>.writeTo(writer: BufferedWriter, tag: String) {
  with(writer) {
    if (isEmpty()) {
      write("  <$tag/>")
    } else {
      write("  <$tag>")
      newLine()
      joinTo(writer, separator = "\n") { "    <ID>${StringEscapeUtils.escapeXml11(it)}</ID>" }
      newLine()
      write("  </$tag>")
    }
  }
}
