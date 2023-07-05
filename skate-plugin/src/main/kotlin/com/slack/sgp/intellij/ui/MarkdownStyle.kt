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
        "h2 { font-size: 1.5em; font-weight: bold}",
        "h3 { font-size: 1.17em; }",
        "h4 { font-size: 1.12em; }",
        "h5 { font-size: .83em; }",
        "h6 { font-size: .75em; }",

        /* Links */
        "a { color: #0366d6; }",

        /* Blockquotes */
        "blockquote { padding: 0 1em; color: #6a737d; border-left: .25em solid #dfe2e5; }",

        /* Code blocks and inline code */
        "pre, code { font-family: monospace; }",
        "pre { padding: 16px; overflow: auto; line-height: 1.45; background-color: #f6f8fa; border-radius: 6px; }",

        /* Lists - not rendering properly */
        "ul li, ol li { margin-left: 2em; }",

        /* Images */
        "img { max-width: 100%; }",

        /*Italics*/
        "em { font-style: italic; }",

        /* Tables */
        "table { border-collapse: collapse; width: 100%; }",
        "table th, table td { padding: 6px 13px; border: 1px solid #dfe2e5; }",
        "table th { font-weight: bold; }",
        "table tr:nth-child(2n) { background-color: #f6f8fa; }",
      )

    return StyleSheet().apply { rules.forEach { rule -> addRule(rule) } }
  }
}
