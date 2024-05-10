package slack.tooling.projectgen.circuitgen

import java.nio.file.Path
import kotlin.io.path.name

fun Path.findJavaPackageName(): Path? {
  return generateSequence(this) { it.parent }
    .find { it.name != "kotlin" && it.name != "java" }
}

fun Path.findSrcDir(): Path {
  return generateSequence(this) { it.parent }
    .takeWhile { it.name != "kotlin" && it.name != "java" }
    .lastOrNull() ?: this
}