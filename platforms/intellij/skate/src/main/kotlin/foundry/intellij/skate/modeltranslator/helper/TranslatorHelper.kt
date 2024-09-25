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
package foundry.intellij.skate.modeltranslator.helper

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiType
import com.intellij.psi.search.GlobalSearchScope
import foundry.intellij.skate.SkatePluginSettings
import foundry.intellij.skate.modeltranslator.model.TranslatorBundle
import foundry.intellij.skate.util.snakeToCamelCase
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.callableBuilder.getReturnTypeReference
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtConstructorCalleeExpression
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.findDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.startOffset

object TranslatorHelper {
  private const val DOT = "."
  private const val TRANSLATOR_FUNC_CALL = ".toDomainModel()"
  private const val UNKNOWN_ENUM_VALUE = "UNKNOWN"
  private const val JSON_NAME_ASSIGNMENT = "(name = \""
  private const val JSON_ANNOTATION_NAME = "Json"

  /**
   * Extracts a bundle containing the necessary information to annotate and generate the model
   * translator if [element] has the header of one and doesn't contain a return statement.
   */
  fun extractBundle(element: PsiElement): TranslatorBundle? {
    val settings = element.project.service<SkatePluginSettings>()

    // Only process this element if it's a named Kotlin function in a translator file.
    if (
      element.containingFile.name.endsWith(settings.translatorFileNameSuffix) &&
        element is KtNamedFunction
    ) {

      // If the function isn't an extension function, then it can't be a translator and no need to
      // process it.
      val sourceModel = element.receiverTypeReference?.text ?: return null

      // The source model might be an inner type, e.g. Call.Action,
      // so get the top most parent type and find the import for it.
      val sourceModelTopMostParent = getTopMostParent(sourceModel)
      val importDirectives = element.containingKtFile.importDirectives
      val sourceModelImport = importDirectives.findImport(sourceModelTopMostParent, sourceModel)

      var isSourceModelString = false

      // If the import, or the fully qualified name, for the source model doesn't start with the
      // right package name,
      // then no need to process this function.
      // The exception is if the source model is a String and the destination is an enum.
      if (
        !(sourceModelImport?.importedFqName?.asString() ?: sourceModel).startsWith(
          settings.translatorSourceModelsPackageName
        ) && (sourceModel != String::class.simpleName).also { isSourceModelString = !it }
      )
        return null

      val bodyExpression = element.bodyBlockExpression
      // If the function doesn't have a body, i.e. a single-expression function, or it contains a
      // return expression,
      // then it's already implemented and no need to process it.
      if (
        bodyExpression == null || bodyExpression.findDescendantOfType<KtReturnExpression>() != null
      )
        return null

      // If the function doesn't have a return type, then it can't be a translator and no need to
      // process it.
      val destinationModelRef = element.getReturnTypeReference()
      val destinationModel = destinationModelRef?.text ?: return null

      // If the source model is a String and the destination isn't an enum,
      // then no need to process this function.
      if (isSourceModelString) {
        val destinationModelClass = getModelClass(importDirectives, destinationModel, element)
        if (destinationModelClass == null || !destinationModelClass.isEnum) {
          return null
        }
      }

      return TranslatorBundle(
        sourceModel,
        destinationModel,
        element,
        importDirectives,
        TextRange.create(element.startOffset, destinationModelRef.endOffset),
      )
    } else {
      return null
    }
  }

  /**
   * Generates and returns the model translator body using the [bundle] information.
   *
   * Example:
   * ```
   * { return Call(id = id, title = title.toDomainModel(), dateStart = dateStart,
   * dateEnded = dateEnd, actions = actions.map { it.toDomainModel() }) }
   * ```
   */
  fun generateBody(bundle: TranslatorBundle): KtBlockExpression? {
    val (sourceModel, destinationModel, element, importDirectives) = bundle

    val modelClass = getModelClass(importDirectives, destinationModel, element)
    if (modelClass != null) {
      val project = element.project
      val bodyBlock =
        KtPsiFactory(project)
          .createBlock(
            if (modelClass.isEnum) {
              generateEnumBody(modelClass, sourceModel, destinationModel, project)
            } else {
              generateClassBody(modelClass, destinationModel)
            }
          )
      return bodyBlock
    }
    return null
  }

  private fun getModelClass(
    importDirectives: List<KtImportDirective>,
    destinationModel: String,
    element: KtNamedFunction,
  ): PsiClass? {
    val destinationModelTopMostParent = getTopMostParent(destinationModel)
    val destinationModelImport =
      importDirectives.findImport(destinationModelTopMostParent, destinationModel)
    val destinationModelFqImport =
      destinationModelImport?.importedFqName?.asString() ?: destinationModel
    val destinationModelFqName =
      if (destinationModelTopMostParent != null)
        "$destinationModelFqImport${destinationModel.removePrefix(destinationModelTopMostParent)}"
      else destinationModelFqImport

    val project = element.project
    return JavaPsiFacade.getInstance(project)
      .findClass(destinationModelFqName, GlobalSearchScope.projectScope(project))
  }

  private fun List<KtImportDirective>.findImport(
    modelTopMostParent: String?,
    model: String,
  ): KtImportDirective? {
    return firstOrNull {
      val id = it.importedName?.identifier
      id == (modelTopMostParent ?: model)
    }
  }

  private fun generateEnumBody(
    modelClass: PsiClass,
    sourceModel: String,
    destinationModel: String,
    project: Project,
  ): String {
    val lineSeparator = System.lineSeparator()
    val whenBlock =
      if (sourceModel == String::class.simpleName)
        generateStringToEnumTranslatorBody(modelClass, lineSeparator, destinationModel, project)
      else
        generateEnumToEnumTranslatorBody(modelClass, lineSeparator, sourceModel, destinationModel)
    return "return when(this) {$lineSeparator$whenBlock$lineSeparator}"
  }

  private fun generateEnumToEnumTranslatorBody(
    modelClass: PsiClass,
    lineSeparator: String,
    sourceModel: String,
    destinationModel: String,
  ): String {
    return modelClass.fields.reversed().joinToString(lineSeparator) {
      if (it.name != UNKNOWN_ENUM_VALUE) "$sourceModel.${it.name} -> $destinationModel.${it.name}"
      else "else -> $destinationModel.${it.name}"
    }
  }

  private fun generateStringToEnumTranslatorBody(
    modelClass: PsiClass,
    lineSeparator: String,
    destinationModel: String,
    project: Project,
  ): String {

    val settings = project.service<SkatePluginSettings>()
    val enumIdentifier = settings.translatorEnumIdentifier

    return modelClass.fields.reversed().joinToString(lineSeparator) {
      if (it.name != UNKNOWN_ENUM_VALUE)
        "$destinationModel.${it.name}.$enumIdentifier -> $destinationModel.${it.name}"
      else "else -> $destinationModel.${it.name}"
    }
  }

  private fun generateClassBody(modelClass: PsiClass, destinationModel: String): String {
    val primaryConstructor = modelClass.constructors[0]
    val params = primaryConstructor.parameterList.parameters

    val core = generateAssignments(params)

    return "return ${destinationModel}($core)"
  }

  private fun generateAssignments(params: Array<PsiParameter>): String {
    return params.joinToString(", ") { param ->
      val annotations = param.annotations

      val value = getJsonName(annotations) ?: param.name

      val (isKnownType, isInCollection) = param.type.extractTypeInfo()
      "${param.name} = ${value.snakeToCamelCase()}${maybeCallToDomainModel(isKnownType, isInCollection)}"
    }
  }

  private fun getJsonName(annotations: Array<out PsiAnnotation>): String? {
    val hasJsonAnnotation =
      annotations.findAnnotation(JSON_ANNOTATION_NAME) { it is KtConstructorCalleeExpression } !=
        null
    return if (hasJsonAnnotation) {
      annotations
        .findAnnotation(JSON_NAME_ASSIGNMENT) { it is KtValueArgumentList }
        ?.let {
          val name = it.findChild { child -> child is KtValueArgumentList }
          name?.text?.substringAfter(JSON_NAME_ASSIGNMENT)?.substringBefore("\"")
        }
    } else {
      null
    }
  }

  private fun Array<out PsiAnnotation>.findAnnotation(
    prefix: String,
    predicate: (PsiElement) -> Boolean,
  ): PsiAnnotation? {
    return firstOrNull {
      val element = it.findChild(predicate)
      element?.text?.startsWith(prefix) == true
    }
  }

  private fun PsiAnnotation.findChild(predicate: (PsiElement) -> Boolean): PsiElement? {
    return nameReferenceElement?.children?.firstOrNull(predicate)
  }

  private fun maybeCallToDomainModel(isKnownType: Boolean, isInCollection: Boolean): String {
    return if (!isKnownType) {
      if (isInCollection) ".map { it$TRANSLATOR_FUNC_CALL }" else TRANSLATOR_FUNC_CALL
    } else {
      ""
    }
  }

  private fun PsiType.extractTypeInfo(): TypeInfo {
    val type = canonicalText
    val (innerType, isInCollection) =
      if (type.contains("<")) {
        type.substringAfter("<").substringBefore(">") to true
      } else {
        type to false
      }

    return TypeInfo(isKnownType = innerType.isPlatformType(), isInCollection = isInCollection)
  }

  private fun String.isPlatformType(): Boolean {
    return !contains(DOT) ||
      contains("java.lang.") ||
      contains("kotlin.") ||
      contains("kotlinx.") ||
      contains("android.") ||
      contains("androidx.")
  }

  private fun getTopMostParent(model: String): String? {
    return if (model.contains(DOT) && model.first().isUpperCase()) model.split(DOT).getOrNull(0)
    else null
  }
}

private data class TypeInfo(val isKnownType: Boolean, val isInCollection: Boolean)
