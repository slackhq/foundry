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
package foundry.cli.lint

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.path
import com.google.auto.service.AutoService
import com.tickaroo.tikxml.converter.htmlescape.StringEscapeUtils
import foundry.cli.CommandFactory
import foundry.cli.projectDirOption
import foundry.cli.sarif.BASELINE_SUPPRESSION
import foundry.cli.sarif.levelOption
import foundry.cli.skipBuildAndCacheDirs
import foundry.cli.walkEachFile
import io.github.detekt.sarif4k.ArtifactContent
import io.github.detekt.sarif4k.ArtifactLocation
import io.github.detekt.sarif4k.Level
import io.github.detekt.sarif4k.Location
import io.github.detekt.sarif4k.Message
import io.github.detekt.sarif4k.MultiformatMessageString
import io.github.detekt.sarif4k.PhysicalLocation
import io.github.detekt.sarif4k.Region
import io.github.detekt.sarif4k.ReportingConfiguration
import io.github.detekt.sarif4k.ReportingDescriptor
import io.github.detekt.sarif4k.Result
import io.github.detekt.sarif4k.Run
import io.github.detekt.sarif4k.SarifSchema210
import io.github.detekt.sarif4k.SarifSerializer
import io.github.detekt.sarif4k.Tool
import io.github.detekt.sarif4k.ToolComponent
import io.github.detekt.sarif4k.Version
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createFile
import kotlin.io.path.createParentDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.relativeTo
import kotlin.io.path.writeText
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.serializer
import nl.adaptivity.xmlutil.serialization.XML
import nl.adaptivity.xmlutil.serialization.XmlSerialName

/** A CLI that merges lint baseline xml files into one. */
public class LintBaselineMergerCli : CliktCommand(DESCRIPTION) {
  private companion object {
    const val DESCRIPTION = "Merges multiple lint baselines into one"
  }

  @AutoService(CommandFactory::class)
  public class Factory : CommandFactory {
    override val key: String = "merge-lint-baselines"
    override val description: String = DESCRIPTION

    override fun create(): CliktCommand = LintBaselineMergerCli()
  }

  private val projectDir by projectDirOption()

  private val baselineFileName by option("--baseline-file-name").required()

  private val outputFile by option("--output-file", "-o").path(canBeDir = false).required()

  private val messageTemplate by
    option(
        "--message-template",
        "-m",
        help =
          "Template for messages with each issue. This message can optionally " +
            "contain '{id}' in it to be replaced with the issue ID and '{message}' " +
            "for the original message.",
      )
      .default("{message}")

  private val level by levelOption().default(Level.Error)

  private val verbose by option("--verbose", "-v").flag()

  private val xml = XML { defaultPolicy { ignoreUnknownChildren() } }

  override fun run() {
    val issues = parseIssues()

    if (verbose) println("Merging ${issues.size} issues")

    if (verbose) println("Gathering rules")
    val rules =
      issues.keys
        .map { issue ->
          ReportingDescriptor(
            id = issue.id,
            name = issue.id,
            shortDescription = MultiformatMessageString(text = issue.message),
            fullDescription = MultiformatMessageString(text = issue.message),
            defaultConfiguration = ReportingConfiguration(level = Level.Error),
          )
        }
        .sortedBy { it.id }
    val ruleIndices = rules.withIndex().associate { (index, rule) -> rule.id to index.toLong() }

    if (verbose) println("Writing to $outputFile")
    outputFile.deleteIfExists()
    outputFile.createParentDirectories()
    outputFile.createFile()
    val outputSarif =
      SarifSchema210(
        version = Version.The210,
        runs =
          listOf(
            Run(
              tool = Tool(ToolComponent(name = "lint", rules = rules)),
              results =
                issues.keys
                  .sortedWith(
                    compareBy(
                      { it.id },
                      { it.location.file },
                      { it.location.line },
                      { it.location.column },
                    )
                  )
                  .map { key -> key to issues.getValue(key) }
                  .map { (issue, projectPath) ->
                    val id = issue.id
                    Result(
                      ruleID = id,
                      level = level,
                      ruleIndex = ruleIndices.getValue(id),
                      locations = listOf(issue.toLocation(projectPath)),
                      suppressions = listOf(BASELINE_SUPPRESSION),
                      message =
                        Message(
                          text =
                            messageTemplate.replace("{id}", id).replace("{message}", issue.message)
                        ),
                    )
                  },
            )
          ),
      )

    SarifSerializer.toJson(outputSarif).let { outputFile.writeText(it) }
  }

  @OptIn(ExperimentalPathApi::class)
  private fun parseIssues(): Map<LintIssues.LintIssue, Path> {
    val issues = mutableMapOf<LintIssues.LintIssue, Path>()
    projectDir
      .walkEachFile { skipBuildAndCacheDirs() }
      .filter { it.name == baselineFileName }
      .forEach { file ->
        if (verbose) println("Parsing $file")
        val lintIssues = xml.decodeFromString(serializer<LintIssues>(), file.readText())
        for (issue in lintIssues.issues) {
          if (verbose) println("Parsed $issue")
          issues[issue] = file.parent
        }
      }

    return issues
  }

  /**
   * ```
   * <issues format="6" by="lint 8.2.0-alpha10" type="baseline" client="cli" dependencies="false"
   *   name="AGP (8.1.2)" variant="all" version="8.2.0-alpha10">
   *     <issue
   *         id="DoNotMockDataClass"
   *         message="&apos;slack.model.account.Account&apos; is a data class, so mocking it should not be necessary"
   *         errorLine1="  @Mock private lateinit var mockAccount1a: Account"
   *         errorLine2="  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~">
   *         <location
   *             file="src/test/java/slack/app/di/CachedOrgComponentProviderTest.kt"
   *             line="46"
   *             column="3"/>
   *     </issue>
   * ```
   */
  @Serializable
  @XmlSerialName("issues")
  internal data class LintIssues(
    val format: Int,
    @Serializable(HtmlEscapeStringSerializer::class) val by: String,
    @Serializable(HtmlEscapeStringSerializer::class) val type: String,
    @Serializable(HtmlEscapeStringSerializer::class) val client: String,
    val dependencies: Boolean,
    @Serializable(HtmlEscapeStringSerializer::class) val name: String,
    @Serializable(HtmlEscapeStringSerializer::class) val variant: String,
    @Serializable(HtmlEscapeStringSerializer::class) val version: String,
    val issues: List<LintIssue>,
  ) {
    @Serializable
    @XmlSerialName("issue")
    data class LintIssue(
      val id: String,
      @Serializable(HtmlEscapeStringSerializer::class) val message: String,
      @Serializable(HtmlEscapeStringSerializer::class) val errorLine1: String,
      @Serializable(HtmlEscapeStringSerializer::class) val errorLine2: String,
      val location: LintLocation,
    ) {
      @Serializable
      @XmlSerialName("location")
      data class LintLocation(
        @Serializable(HtmlEscapeStringSerializer::class) val file: String,
        val line: Long?,
        val column: Long?,
      )
    }
  }

  /**
   * A String TypeConverter that escapes and unescapes HTML characters directly from string. This
   * one uses apache 3 StringEscapeUtils borrowed from tikxml.
   */
  internal object HtmlEscapeStringSerializer : KSerializer<String> {

    override val descriptor: SerialDescriptor =
      PrimitiveSerialDescriptor("EscapedString", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String {
      return StringEscapeUtils.unescapeHtml4(decoder.decodeString())
    }

    override fun serialize(encoder: Encoder, value: String) {
      encoder.encodeString(StringEscapeUtils.escapeHtml4(value))
    }
  }

  private fun LintIssues.LintIssue.toLocation(projectPath: Path): Location {
    val uri = projectPath.resolve(location.file).relativeTo(projectDir).toString()
    return Location(
      physicalLocation =
        PhysicalLocation(
          artifactLocation = ArtifactLocation(uri = uri),
          region =
            Region(
              startLine = location.line,
              startColumn = location.column,
              snippet =
                ArtifactContent(
                  text =
                    """
                      $errorLine1
                      $errorLine2
                    """
                      .trimIndent()
                ),
            ),
        )
    )
  }
}
