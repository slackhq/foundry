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
package foundry.intellij.skate.modeltranslator.model

import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtNamedFunction

/**
 * Contains the necessary information to annotate and generate a model translator.
 *
 * @property sourceModel The model to translate from.
 * @property destinationModel The model to translate to.
 * @property element The translator function.
 * @property importDirectives The list of imports in the translator file.
 * @property functionHeaderRange The text range from the begging of the translator function until
 *   the end of the [destinationModel].
 */
data class TranslatorBundle(
  val sourceModel: String,
  val destinationModel: String,
  val element: KtNamedFunction,
  val importDirectives: List<KtImportDirective>,
  val functionHeaderRange: TextRange,
)
