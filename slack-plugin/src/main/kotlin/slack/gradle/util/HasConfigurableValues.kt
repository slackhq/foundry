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
package slack.gradle.util

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.SetProperty

/*
 * APIs adapted from `HasConfigurableValues.kt` in AGP. Copied for binary safety.
 */

internal fun ConfigurableFileCollection.fromDisallowChanges(vararg arg: Any) {
  from(*arg)
  disallowChanges()
}

internal fun <T> Property<T>.setDisallowChanges(value: T?) {
  set(value)
  disallowChanges()
}

internal fun <T> Property<T>.setDisallowChanges(value: Provider<out T>) {
  set(value)
  disallowChanges()
}

internal fun <T> ListProperty<T>.setDisallowChanges(value: Provider<out Iterable<T>>) {
  set(value)
  disallowChanges()
}

internal fun <T> ListProperty<T>.setDisallowChanges(value: Iterable<T>?) {
  set(value)
  disallowChanges()
}

internal fun <K, V> MapProperty<K, V>.setDisallowChanges(map: Provider<Map<K, V>>) {
  set(map)
  disallowChanges()
}

internal fun <K, V> MapProperty<K, V>.setDisallowChanges(map: Map<K, V>?) {
  set(map)
  disallowChanges()
}

internal fun <T> SetProperty<T>.setDisallowChanges(value: Provider<out Iterable<T>>) {
  set(value)
  disallowChanges()
}

internal fun <T> SetProperty<T>.setDisallowChanges(value: Iterable<T>?) {
  set(value)
  disallowChanges()
}

internal fun <T> ListProperty<T>.setDisallowChanges(
  value: Provider<out Iterable<T>>?,
  handleNullable: ListProperty<T>.() -> Unit,
) {
  value?.let { set(value) } ?: handleNullable()
  disallowChanges()
}

internal fun <K, V> MapProperty<K, V>.setDisallowChanges(
  map: Provider<Map<K, V>>?,
  handleNullable: MapProperty<K, V>.() -> Unit,
) {
  map?.let { set(map) } ?: handleNullable()
  disallowChanges()
}
