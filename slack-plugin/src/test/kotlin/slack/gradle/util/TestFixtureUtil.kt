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
package slack.gradle.util

import com.squareup.javapoet.JavaFile
import com.squareup.javapoet.MethodSpec
import com.squareup.javapoet.TypeSpec
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import java.io.File
import javax.lang.model.element.Modifier.PUBLIC
import slack.gradle.util.SourceFile.JavaSourceFile
import slack.gradle.util.SourceFile.KotlinSourceFile

internal fun javaFile(
  packageName: String,
  className: String,
  body: TypeSpec.Builder.() -> Unit
): SourceFile {
  return JavaFile.builder(
      packageName,
      TypeSpec.classBuilder(className).addModifiers(PUBLIC).apply(body).build()
    )
    .build()
    .asSourceFile()
}

internal fun TypeSpec.Builder.methodSpec(name: String, body: MethodSpec.Builder.() -> Unit) {
  addMethod(MethodSpec.methodBuilder(name).addModifiers(PUBLIC).apply(body).build())
}

internal fun kotlinFile(
  packageName: String,
  className: String,
  body: com.squareup.kotlinpoet.TypeSpec.Builder.() -> Unit
): SourceFile {
  return FileSpec.get(
      packageName = packageName,
      typeSpec = com.squareup.kotlinpoet.TypeSpec.objectBuilder(className).apply(body).build()
    )
    .asSourceFile()
}

internal fun com.squareup.kotlinpoet.TypeSpec.Builder.funSpec(
  name: String,
  body: FunSpec.Builder.() -> Unit
) {
  addFunction(FunSpec.builder(name).apply(body).build())
}

internal operator fun File.plusAssign(fileSpec: FileSpec) {
  fileSpec.writeTo(this)
}

internal fun File.newFile(path: String, block: (File.() -> Unit)? = null): File {
  return File(this, path).apply {
    parentFile.mkdirs()
    block?.invoke(this)
  }
}

internal fun File.newDir(path: String): File {
  return File(this, path).apply { mkdirs() }
}

internal fun File.child(vararg path: String) =
  File(this, path.toList().joinToString(File.separator)).apply {
    check(exists()) {
      "Child doesn't exist! Expected $this. Other files in this dir: ${parentFile.listFiles()}"
    }
  }

internal sealed class SourceFile(val name: String) {
  abstract fun writeTo(file: File)

  data class JavaSourceFile(val javaFile: JavaFile) : SourceFile(javaFile.typeSpec.name) {
    override fun writeTo(file: File) = javaFile.writeTo(file)
  }

  data class KotlinSourceFile(val fileSpec: FileSpec) :
    SourceFile(fileSpec.members.filterIsInstance<TypeSpec>().first().name!!) {
    override fun writeTo(file: File) = fileSpec.writeTo(file)
  }
}

internal operator fun File.plusAssign(sourceFile: SourceFile) {
  sourceFile.writeTo(this)
}

internal fun JavaFile.asSourceFile(): SourceFile = JavaSourceFile(this)

internal fun FileSpec.asSourceFile(): SourceFile = KotlinSourceFile(this)
