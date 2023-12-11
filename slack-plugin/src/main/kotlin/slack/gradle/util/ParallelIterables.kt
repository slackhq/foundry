package slack.gradle.util

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore

/**
 * Applies a given [mapper] function in parallel over this [Iterable] with a given [parallelism].
 */
internal suspend inline fun <A, B> Iterable<A>.parallelMap(
  parallelism: Int,
  crossinline mapper: suspend (A) -> B
): List<B> = coroutineScope {
  val semaphore = Semaphore(parallelism)
  map {
    semaphore.acquire()
    async(start = CoroutineStart.UNDISPATCHED) {
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
internal suspend inline fun <A, B> Iterable<A>.parallelMapNotNull(
  parallelism: Int,
  crossinline mapper: suspend (A) -> B?
): List<B> = coroutineScope {
  val semaphore = Semaphore(parallelism)
  map {
    semaphore.acquire()
    async(start = CoroutineStart.UNDISPATCHED) {
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
internal suspend inline fun <A> Iterable<A>.parallelForEach(
  parallelism: Int,
  crossinline action: suspend (A) -> Unit
): Unit = coroutineScope {
  val semaphore = Semaphore(parallelism)
  forEach {
    semaphore.acquire()
    launch(start = CoroutineStart.UNDISPATCHED) {
      try {
        action(it)
      } finally {
        semaphore.release()
      }
    }
  }
}