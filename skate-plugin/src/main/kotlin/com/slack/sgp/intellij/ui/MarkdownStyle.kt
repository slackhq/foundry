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
package com.slack.sgp.intellij.ui

import javax.swing.text.html.StyleSheet

object MarkdownStyle {
  fun createStyleSheet(): StyleSheet {
    val rules =
      listOf(
        /* General body text */
        "body { width: fit-content; font-family: Arial, sans-serif; line-height: 1.6; color: #FFF; padding: 10px; }",

        /* Headings */
        "h1, h2, h3, h4, h5, h6 { margin-top: 20px; margin-bottom: 10px; }",
        "h1 { font-size: 2.5em; font-weight: bold}",
        "h2 { font-size: 2.0em; font-weight: bold}",
        "h3 { font-size: 1.5em; font-weight: bold}",
        "h4 { font-size: 1.12em; }",
        "h5 { font-size: .83em; }",
        "h6 { font-size: .75em; }",

        /* Links */
        "a { color: #0366d6; }",

        /* Blockquotes */
        "blockquote { padding: 0 1em; color: #FFF; background-color: #464140; border-left: .25em solid #dfe2e5; }",

        /* Code blocks and inline code */
        "pre, code { display: block; background-color: #5A5A5A; font-family: monospace; padding-bottom: 10px; word-warp: normal; overflow: auto; }",
        "pre { padding: 8px; line-height: 1; margin-bottom: 10px}",

        /* Lists */
        "ul, ol { display: inline-block; list-style-type: disc; padding-left: 15px; margin-bottom: 5px; }",

        /* Images */
        "img { max-width: 100%; }",

        /*Italics*/
        "em { font-style: italic; font-size: 1.2em; }",

        /* Paragraphs */
        "p { word-wrap: break-word; padding-bottom: 5px; }"
      )

    return StyleSheet().apply { rules.forEach { rule -> addRule(rule) } }
  }
}
