package foundry.common

import java.util.TreeMap
import org.intellij.lang.annotations.Language

/**
 * A [Map] that can have [Regex] keys. This requires a custom impl because [Regex] is not
 * comparable, so we internally back these with a [TreeMap] rather than the conventional
 * [LinkedHashMap].
 */
public class RegexMap internal constructor(delegate: Map<Regex, String>) :
  Map<Regex, String> by delegate {
  public constructor() : this(emptyMap())
}

public fun buildRegexMap(body: RegexMapBuilder.() -> Unit): RegexMap =
  RegexMap(RegexMapBuilderImpl().apply(body).map)

public interface RegexMapBuilder {
  public fun remove(@Language("RegExp") regex: String): Unit = replace(regex, "")

  public fun replace(@Language("RegExp") regex: String, replacement: String)
}

private class RegexMapBuilderImpl : RegexMapBuilder {
  val map = TreeMap<Regex, String>(compareBy { it.pattern })

  override fun replace(regex: String, replacement: String) {
    map.put(regex.toRegex(), replacement)
  }
}
