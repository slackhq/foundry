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
@file:Suppress("TooManyFunctions")

package slack.gradle

import com.android.build.gradle.AppPlugin
import com.android.build.gradle.LibraryPlugin
import com.android.build.gradle.internal.dsl.BuildType
import com.android.builder.model.AndroidProject
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.reflect.javaType
import kotlin.reflect.typeOf
import org.gradle.api.Action
import org.gradle.api.DomainObjectSet
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.UnknownTaskException
import org.gradle.api.artifacts.ArtifactView
import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.Attribute
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.AppliedPlugin
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.plugins.ExtraPropertiesExtension
import org.gradle.api.plugins.PluginManager
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.reflect.TypeOf
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.configurationcache.extensions.get
import org.gradle.internal.service.ServiceRegistry

/*
 * A set of utility functions that check and cache project information stored in extensions.
 */

private const val IS_ANDROID = "slack.project.ext.isAndroid"
private const val IS_ANDROID_APPLICATION = "slack.project.ext.isAndroidApplication"
private const val IS_ANDROID_LIBRARY = "slack.project.ext.isAndroidLibrary"
private const val IS_USING_KAPT = "slack.project.ext.isUsingKapt"
private const val IS_USING_KSP = "slack.project.ext.isUsingKsp"
private const val IS_USING_MOSHI_IR = "slack.project.ext.isUsingMoshiIr"
private const val IS_KOTLIN = "slack.project.ext.isKotlin"
private const val IS_KOTLIN_ANDROID = "slack.project.ext.isKotlinAndroid"
private const val IS_KOTLIN_JVM = "slack.project.ext.isKotlinJvm"
private const val IS_JAVA_LIBRARY = "slack.project.ext.isJavaLibrary"
private const val IS_JAVA = "slack.project.ext.isJava"

internal val Project.isRootProject: Boolean
  get() = rootProject === this

internal val Project.isJava: Boolean
  get() {
    return getOrComputeExt(IS_JAVA) { isJavaLibrary || project.pluginManager.hasPlugin("java") }
  }

internal val Project.isJavaLibrary: Boolean
  get() {
    return getOrComputeExt(IS_JAVA_LIBRARY) { project.pluginManager.hasPlugin("java-library") }
  }

internal val Project.isKotlin: Boolean
  get() {
    return getOrComputeExt(IS_KOTLIN) { isKotlinAndroid || isKotlinJvm }
  }

internal val Project.isKotlinAndroid: Boolean
  get() {
    return getOrComputeExt(IS_KOTLIN_ANDROID) {
      project.pluginManager.hasPlugin("org.jetbrains.kotlin.android")
    }
  }

internal val Project.isKotlinJvm: Boolean
  get() {
    return getOrComputeExt(IS_KOTLIN_JVM) {
      project.pluginManager.hasPlugin("org.jetbrains.kotlin.jvm")
    }
  }

internal val Project.isUsingKapt: Boolean
  get() {
    return getOrComputeExt(IS_USING_KAPT) {
      project.pluginManager.hasPlugin("org.jetbrains.kotlin.kapt")
    }
  }

internal val Project.isUsingKsp: Boolean
  get() {
    return getOrComputeExt(IS_USING_KSP) {
      project.pluginManager.hasPlugin("com.google.devtools.ksp")
    }
  }

internal val Project.isUsingMoshiGradle: Boolean
  get() {
    return getOrComputeExt(IS_USING_MOSHI_IR) {
      project.pluginManager.hasPlugin("dev.zacsweers.moshix")
    }
  }

internal val Project.isAndroidApplication: Boolean
  get() {
    return getOrComputeExt(IS_ANDROID_APPLICATION) { plugins.hasPlugin(AppPlugin::class.java) }
  }

internal val Project.isAndroidLibrary: Boolean
  get() {
    return getOrComputeExt(IS_ANDROID_LIBRARY) { plugins.hasPlugin(LibraryPlugin::class.java) }
  }

internal val Project.isAndroid: Boolean
  get() {
    return getOrComputeExt(IS_ANDROID) { isAndroidApplication || isAndroidLibrary }
  }

internal fun <T : Any> Project.getOrComputeExt(key: String, valueCalculator: () -> T): T {
  @Suppress("UNCHECKED_CAST")
  return (extensions.findByName(key) as? T)
    ?: run {
      val value = valueCalculator()
      extensions.add(key, value)
      return value
    }
}

/** Lifts an action into another action, reusing this action instance as an input to [into]. */
internal fun <T, R : Any> Action<T>.liftIntoAction(into: R.(task: Action<T>) -> Unit): Action<R> {
  return Action { into(this@liftIntoAction) }
}

/** Lifts an action into another action, reusing this action instance as an input to [into]. */
internal fun <T, R> Action<T>.liftIntoFunction(into: R.(task: Action<T>) -> Unit): R.() -> Unit {
  return { into(this@liftIntoFunction) }
}

internal inline fun <reified T : Task> TaskContainer.register(
  name: String,
  configuration: Action<in T>
): TaskProvider<T> = register(name, T::class.java, configuration)

internal inline fun <reified T : Any> Project.configure(action: Action<T>) {
  extensions.getByType<T>().apply(action::execute)
}

internal inline fun <reified T> ExtensionContainer.findByType(): T? {
  // Gradle, Kotlin, and Java all have different notions of what a "type" is.
  // I'm sorry
  return findByType(TypeOf.typeOf(typeOf<T>().javaType))
}

internal inline fun <reified T : Task> TaskContainer.configureEach(noinline action: T.() -> Unit) {
  withType(T::class.java).configureEach(action)
}

internal inline fun <reified T> ExtensionContainer.getByType(): T {
  // Gradle, Kotlin, and Java all have different notions of what a "type" is.
  // I'm sorry
  return getByType(TypeOf.typeOf(typeOf<T>().javaType))
}

internal inline fun <reified T : Task> TaskContainer.providerWithNameOrNull(
  name: String
): TaskProvider<T>? {
  return try {
    named(name, T::class.java)
  } catch (e: UnknownTaskException) {
    null
  }
}

internal fun TaskContainer.providerWithNameOrNull(
  name: String,
  action: Action<Task>
): TaskProvider<Task>? {
  return try {
    named(name, action)
  } catch (e: UnknownTaskException) {
    null
  }
}

/**
 * Best-effort tries to apply an [action] on a task with matching [name]. If the task doesn't exist
 * at the time this is called, a [TaskContainer.whenTaskAdded] callback is added to match on the
 * name and execute the action when it's added.
 *
 * This approach has caveats, namely that you won't get an immediate failure or indication if you've
 * requested action on a task that may never be added. This is intended to be similar to the
 * behavior of [PluginManager.withPlugin].
 */
internal fun TaskContainer.withName(name: String, action: Action<Task>) {
  try {
    named(name, action)
  } catch (e: UnknownTaskException) {
    whenTaskAdded {
      if (this@whenTaskAdded.name == name) {
        action.execute(this)
      }
    }
  }
}

@Suppress("SpreadOperator")
public fun <T : Task> TaskProvider<out T>.dependsOn(
  vararg tasks: TaskProvider<out Task>
): TaskProvider<out T> {
  if (tasks.isEmpty().not()) {
    configure { dependsOn(*tasks) }
  }

  return this
}

/** Returns an [ArtifactView] of android configuration artifacts. */
internal fun Configuration.androidArtifactView(): ArtifactView {
  return incoming.artifactView {
    attributes { attribute(Attribute.of("artifactType", String::class.java), "android-classes") }
  }
}

internal operator fun ExtensionContainer.set(key: String, value: Any) {
  add(key, value)
}

/** Retrieves the [ext][ExtraPropertiesExtension] extension. */
internal val BuildType.ext: ExtraPropertiesExtension
  get() = (this as ExtensionAware).extensions.getByName("ext") as ExtraPropertiesExtension

internal fun PluginManager.onFirst(
  pluginIds: Iterable<String>,
  body: AppliedPlugin.(id: String) -> Unit
) {
  once {
    for (id in pluginIds) {
      withPlugin(id) { onFirst { body(id) } }
    }
  }
}

internal inline fun once(body: OnceCheck.() -> Unit) {
  contract { callsInPlace(body, InvocationKind.EXACTLY_ONCE) }
  OnceCheck().body()
}

@JvmInline
internal value class OnceCheck(val once: AtomicBoolean = AtomicBoolean(false)) {
  inline val isActive: Boolean
    get() = once.compareAndSet(false, true)

  inline fun onFirst(body: () -> Unit) {
    if (isActive) {
      body()
    }
  }
}

/**
 * Returns true if this execution of Gradle is for an Android Studio Gradle Sync. We're considering
 * both the no-task invocation of Gradle that AS uses to build its model, and the invocation of
 * "generateXSources" for each project that follows it. (We may want to track these in the future
 * too, but for now they're pretty noisy.)
 */
internal val Project.isSyncing: Boolean
  get() =
    invokedFromIde &&
      (findProperty(AndroidProject.PROPERTY_BUILD_MODEL_ONLY) == "true" ||
        findProperty(AndroidProject.PROPERTY_GENERATE_SOURCES_ONLY) == "true")

// Note that we don't reference the AndroidProject property because this constant moved in AGP 7.2
public val Project.invokedFromIde: Boolean
  get() = hasProperty("android.injected.invoked.from.ide")

internal inline fun <reified T : Any> ObjectFactory.newInstance(vararg parameters: Any): T {
  return newInstance(T::class.java, *parameters)
}

internal inline fun <reified T : Any> ObjectFactory.property(): Property<T> {
  return property(T::class.java)
}

internal inline fun <reified E : Any> ObjectFactory.setProperty(): SetProperty<E> {
  return setProperty(E::class.java)
}

internal inline fun <reified E : Any> ObjectFactory.listProperty(): ListProperty<E> {
  return listProperty(E::class.java)
}

internal inline fun <reified K : Any, reified V : Any> ObjectFactory.mapProperty():
  MapProperty<K, V> {
  return mapProperty(K::class.java, V::class.java)
}

internal inline fun <reified E : Any> ObjectFactory.domainObjectSet(): DomainObjectSet<E> {
  return domainObjectSet(E::class.java)
}

internal inline fun <reified T : Any> Project.serviceOf(): T =
  (this as ProjectInternal).services.get()

internal inline fun <reified T : Any> ServiceRegistry.get(): T = this[T::class.java]!!
