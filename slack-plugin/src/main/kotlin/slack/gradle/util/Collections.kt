package slack.gradle.util

internal fun <T, R> Collection<T>.mapToSet(transform: (T) -> R): Set<R> {
  return mapTo(mutableSetOf(), transform)
}

internal fun <T, R> Collection<T>.flatMapToSet(transform: (T) -> Iterable<R>): Set<R> {
  return flatMapTo(mutableSetOf(), transform)
}