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
package slack.gradle.agphandler.v74

import com.android.build.api.dsl.CommonExtension
import com.android.build.gradle.internal.dsl.TestOptions
import com.android.builder.model.Version.ANDROID_GRADLE_PLUGIN_VERSION
import com.google.auto.service.AutoService
import groovy.lang.Closure
import org.gradle.api.tasks.testing.Test
import slack.gradle.agp.AgpHandler
import slack.gradle.agp.AgpHandlerFactory
import slack.gradle.agp.VersionNumber

@AutoService(AgpHandlerFactory::class)
public class AgpHandlerFactory74 : AgpHandlerFactory {
  override val minVersion: VersionNumber = VersionNumber.parse("7.4.0")

  override fun currentVersion(): String = ANDROID_GRADLE_PLUGIN_VERSION

  override fun create(): AgpHandler {
    return AgpHandler74()
  }
}

private class AgpHandler74 : AgpHandler {
  override val agpVersion: String
    get() = ANDROID_GRADLE_PLUGIN_VERSION

  override fun allUnitTestOptions(options: TestOptions.UnitTestOptions, body: (Test) -> Unit) {
    options.all(typedClosureOf(body))
  }

  override fun packagingOptions(
    commonExtension: CommonExtension<*, *, *, *>,
    resourceExclusions: Collection<String>,
    jniPickFirsts: Collection<String>
  ) {
    commonExtension.packagingOptions {
      resources.excludes += resourceExclusions
      jniLibs.pickFirsts += jniPickFirsts
    }
  }
}

/** Typed alternative to Gradle Kotlin-DSL's [closureOf], which only returns `Closure<Any?>`. */
private fun <T> Any.typedClosureOf(action: T.() -> Unit): Closure<T> {
  @Suppress("UNCHECKED_CAST") return closureOf(action) as Closure<T>
}

private fun <T> Any.closureOf(action: T.() -> Unit): Closure<Any?> =
  KotlinClosure1(action, this, this)

private class KotlinClosure1<in T : Any?, V : Any>(
  val function: T.() -> V?,
  owner: Any? = null,
  thisObject: Any? = null
) : Closure<V?>(owner, thisObject) {

  @Suppress("unused") // to be called dynamically by Groovy
  fun doCall(it: T): V? = it.function()
}
