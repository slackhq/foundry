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
package foundry.common

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore

/**
 * Applies a given [mapper] function in parallel over this [Iterable] with a given [parallelism].
 */
public suspend inline fun <A, B> Iterable<A>.parallelMap(
  parallelism: Int,
  start: CoroutineStart = CoroutineStart.DEFAULT,
  crossinline mapper: suspend (A) -> B,
): List<B> = coroutineScope {
  val semaphore = Semaphore(parallelism)
  map {
      semaphore.acquire()
      async(start = start) {
        try {
          mapper(it)
        } finally {
          semaphore.release()
        }
      }
    }
    .awaitAll()
}

/**
 * Applies a given [mapper] function in parallel over this [Iterable] with a given [parallelism],
 * filtering out null [B]s.
 */
public suspend inline fun <A, B> Iterable<A>.parallelMapNotNull(
  parallelism: Int,
  start: CoroutineStart = CoroutineStart.DEFAULT,
  crossinline mapper: suspend (A) -> B?,
): List<B> = coroutineScope {
  val semaphore = Semaphore(parallelism)
  map {
      semaphore.acquire()
      async(start = start) {
        try {
          mapper(it)
        } finally {
          semaphore.release()
        }
      }
    }
    .awaitAll()
    .filterNotNull()
}

/**
 * Iterates this [Iterable] with a given [parallelism], passing the value for each emission to the
 * given [action].
 */
public suspend inline fun <A> Iterable<A>.parallelForEach(
  parallelism: Int,
  start: CoroutineStart = CoroutineStart.DEFAULT,
  crossinline action: suspend (A) -> Unit,
): Unit = coroutineScope {
  val semaphore = Semaphore(parallelism)
  forEach {
    semaphore.acquire()
    launch(start = start) {
      try {
        action(it)
      } finally {
        semaphore.release()
      }
    }
  }
}
