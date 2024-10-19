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
package foundry.gradle.dependencies

import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

internal sealed class DependencyNode {
  /** Returns a flattened set of all [DependencyDefs][DependencyDef]. */
  abstract fun flatten(): Set<DependencyDef>

  data class Leaf(
    val key: String,
    val dependency: DependencyDef,
    val parent: DependencyCollection,
  ) : DependencyNode() {
    override fun flatten(): Set<DependencyDef> {
      return setOf(dependency)
    }
  }

  data class Branch(val pathSegment: String, val nodes: Set<DependencyNode>) : DependencyNode() {
    override fun flatten(): Set<DependencyDef> {
      return nodes.flatMapTo(LinkedHashSet(), DependencyNode::flatten)
    }
  }
}

public interface DependencyCollection {
  public companion object {
    internal const val GRADLE_PROPERTY_PREFIX = "slack.dependencies."
  }
}

/** Flattened bom dependencies of all dependencies contained in this collection. */
public fun DependencyCollection.boms(): List<DependencyGroup> {
  val result = mutableListOf<DependencyGroup>()
  if (this is DependencyGroup) {
    result += this
  }
  result +=
    this::class
      .nestedClasses
      .mapNotNull { it.objectInstance }
      .filterIsInstance<DependencyCollection>()
      .flatMap { it.boms() }
      .filter { it.bomArtifact != null }
  return result
}

/** Flattened dependencies of all dependencies contained in this. */
public fun DependencyCollection.flattenedPlatformCoordinates(): Set<DependencyDef> =
  getDependencies().flatten()

/**
 * Flattened dependencies of all identifiers to their corresponding dependency version gradle
 * properties.
 */
public fun DependencyCollection.identifiersToGradleProperties(
  includeBomManagedDependencies: Boolean
): Map<String, String> =
  flattenedPlatformCoordinates()
    .filterNot { includeBomManagedDependencies && it.isBomManaged }
    .associate { it.identifier to it.gradleProperty }

/**
 * Returns a mapping of identifiers (i.e. "com.foo:bar") to their qualified path within this
 * collection
 */
internal fun DependencyCollection.identifierMap(): Map<String, String> {
  val rootDependencies = getDependencies()
  val rootMap = mutableMapOf<String, String>()
  fun fillMap(currentPath: String, rootNode: DependencyNode) {
    when (rootNode) {
      is DependencyNode.Leaf -> {
        rootMap[rootNode.dependency.identifier] = "$currentPath.${rootNode.key}"
      }
      is DependencyNode.Branch -> {
        rootNode.nodes.forEach { fillMap("$currentPath.${rootNode.pathSegment}", it) }
      }
    }
  }
  rootDependencies.nodes.forEach { fillMap(rootDependencies.pathSegment, it) }

  return rootMap.toMap()
}

internal fun DependencyCollection.getDependencies(): DependencyNode.Branch {
  return walkClass()
}

/**
 * This walks down the properties and subtypes of [this] [DependencyCollection] to create a branch
 * within a larger dependency tree.
 */
private fun DependencyCollection.walkClass(): DependencyNode.Branch {
  val segmentName = this::class.simpleName!!
  val nodes = mutableListOf<DependencyNode>()
  nodes += leaves()
  for (type in this::class.nestedClasses) {
    val objectInstance = type.objectInstance
    if (objectInstance is DependencyCollection) {
      nodes += objectInstance.walkClass()
    }
  }

  return DependencyNode.Branch(segmentName, nodes.toSet())
}

private fun DependencyCollection.leaves(): List<DependencyNode.Leaf> {
  val nodes = mutableListOf<DependencyNode.Leaf>()
  for (prop in this::class.memberProperties) {
    prop.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    val possibleDelegate = (prop as KProperty1<in DependencyCollection, *>).getDelegate(this)
    if (possibleDelegate is DependencyDelegate) {
      val propKey = prop.name
      val def = possibleDelegate.getOrCreateDef(prop)
      nodes += DependencyNode.Leaf(propKey, def, this)
    }
  }
  return nodes
}
