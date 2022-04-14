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
package slack.fakes

import java.util.function.BiFunction
import org.gradle.api.Transformer
import org.gradle.api.internal.provider.MissingValueException
import org.gradle.api.provider.Provider

private object ABSENT

/** A simple [Provider] backed by a [value]. */
class ValueProvider<T : Any> : Provider<T> {

  private val value: Any?

  /** Constructs an un-set instance. */
  constructor() {
    this.value = ABSENT
  }

  /** Constructs an instance based around the input [value]. */
  constructor(value: T?) {
    this.value = value
  }

  @Suppress("NOTHING_TO_INLINE")
  private inline fun checkNotAbsent(value: Any?): T? {
    check(value !== ABSENT) { "Value for property not set" }
    @Suppress("UNCHECKED_CAST") return value as? T
  }

  override fun get(): T {
    return checkNotAbsent(value) ?: throw MissingValueException("No value set for $this")
  }

  override fun getOrNull(): T? {
    return checkNotAbsent(value)
  }

  override fun getOrElse(defaultValue: T): T {
    return checkNotAbsent(value) ?: defaultValue
  }

  override fun <S> map(transformer: Transformer<out S?, in T>): Provider<S> {
    error("Not implemented")
  }

  override fun <S> flatMap(transformer: Transformer<out Provider<out S?>, in T>): Provider<S> {
    error("Not implemented")
  }

  override fun isPresent(): Boolean {
    return value !== ABSENT
  }

  override fun orElse(elseValue: T): Provider<T> {
    return object : Provider<T> by this {
      override fun get(): T {
        if (value === ABSENT) return elseValue
        if (value == null) return elseValue
        @Suppress("UNCHECKED_CAST") return value as T
      }
    }
  }

  override fun orElse(other: Provider<out T>): Provider<T> {
    error("Not implemented")
  }

  override fun forUseAtConfigurationTime(): Provider<T> {
    return this
  }

  override fun <B : Any, R : Any> zip(other: Provider<B>, p1: BiFunction<T, B, R>): Provider<R> {
    error("Not implemented")
  }
}
