/*
 * Copyright (C) 2025 Slack Technologies, LLC
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
package foundry.cli.buildkite

import kotlin.collections.plusAssign
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.intellij.lang.annotations.Language

/** DSL marker for constructing Buildkite Pipelines */
@DslMarker public annotation class BuildkiteDsl

/**
 * Creates and builds a `Pipeline` using the provided configuration block.
 *
 * Example:
 * ```
 * val pipeline = pipeline {
 *   commandStep {
 *     label = "Run tests"
 *     command = "run-tests.sh"
 *     env {
 *       "ENV_VAR" to "value"
 *     }
 *   }
 * }
 * ```
 */
public fun pipeline(block: PipelineBuilder.() -> Unit): Pipeline {
  val builder = PipelineBuilder()
  builder.block()
  return builder.build()
}

@BuildkiteDsl
public class PipelineBuilder : EnvOwner by EnvOwnerImpl() {
  private var agents: Agents? = null
  private val notifications = mutableListOf<Notification>()
  private val steps = mutableListOf<GroupStep>()

  public fun agents(block: AgentsBuilder.() -> Unit) {
    agents = AgentsBuilder().apply(block).build()
  }

  public fun notify(block: NotificationBuilder.() -> Unit) {
    val builder = NotificationBuilder()
    builder.block()
    notifications.addAll(builder.build())
  }

  public fun addStep(step: GroupStep) {
    steps.add(step)
  }

  public fun group(name: String, key: String? = null, block: GroupBuilder.() -> Unit) {
    addStep(buildGroupStep(name, key, block))
  }

  public fun commandStep(block: CommandStepBuilder.() -> Unit) {
    val builder = CommandStepBuilder()
    builder.block()
    addStep(GroupStep(builder.build()))
  }

  public fun waitStep(continueOnFailure: Boolean = true) {
    addStep(
      GroupStep(Wait.WaitStepValue(WaitStep(wait = "~", continueOnFailure = continueOnFailure)))
    )
  }

  public fun build(): Pipeline =
    Pipeline(
      steps = steps,
      agents = agents,
      env = env.takeUnless { it.isEmpty() },
      notify = if (notifications.isEmpty()) null else notifications,
    )
}

public fun buildCommandStep(block: CommandStepBuilder.() -> Unit): CommandStep {
  val builder = CommandStepBuilder()
  builder.block()
  return builder.build()
}

public fun buildGroupStep(
  name: String,
  key: String? = null,
  block: GroupBuilder.() -> Unit,
): GroupStep {
  val builder = GroupBuilder(name, key)
  builder.block()
  return GroupStep(builder.build())
}

@BuildkiteDsl
public class EnvBuilder {
  private val env = mutableMapOf<String, String>()

  public infix fun String.to(value: Boolean) {
    env.put(this, value.toString())
  }

  public infix fun String.to(value: String) {
    env.put(this, value)
  }

  internal fun build() = env.toMap()
}

// Agents Builder
@BuildkiteDsl
public class AgentsBuilder {
  private var anythingMapValue: JsonObject? = null
  private var stringArrayValue: List<String>? = null

  public fun json(value: JsonObject) {
    anythingMapValue = value
  }

  public fun array(value: List<String>) {
    stringArrayValue = value
  }

  public fun build(): Agents? =
    when {
      anythingMapValue != null -> Agents(anythingMapValue!!)
      stringArrayValue != null -> Agents(stringArrayValue!!)
      else -> null
    }
}

// Notification Builder
@BuildkiteDsl
public class NotificationBuilder {
  private val notifications = mutableListOf<Notification>()

  public fun github(notification: GithubNotification) {
    notifications.add(Notification.GitHub(notification))
  }

  public fun external(notification: ExternalNotification) {
    notifications.add(Notification.External(notification))
  }

  public fun build(): List<Notification> = notifications
}

// ScriptStep Builder
@BuildkiteDsl
public class CommandStepBuilder :
  EnvOwner by EnvOwnerImpl(), StepWithDependencies by StepWithDependenciesImpl() {
  public var label: String? = null
  @Language("bash") public var command: String? = null
  public var commands: Commands? = null
  public var key: String? = null
  public var stepIf: String? = null
  public var branch: String? = null
  public var async: Boolean? = null

  // DSL access
  private var branches: SimpleStringValue? = null
  private var artifactPaths: SimpleStringValue? = null
  private var softFail: SoftFail? = null
  private var trigger: Trigger? = null

  // Exposed for use but no intentional API defined
  public var allowDependencyFailure: Boolean? = null
  public var block: String? = null
  public var blockedState: BlockedState? = null
  public var fields: List<FieldElement>? = null
  public var id: String? = null
  public var identifier: String? = null
  public var name: String? = null
  public var prompt: String? = null
  public var type: StepType? = null
  public var agents: Agents? = null
  public var cancelOnBuildFailing: Boolean? = null
  public var concurrency: Long? = null
  public var concurrencyGroup: String? = null
  public var concurrencyMethod: ConcurrencyMethod? = null
  public var matrix: MatrixUnion? = null
  public var notify: MutableList<Notification> = mutableListOf()
  public var parallelism: Long? = null
  public var plugins: Plugins? = null
  public var priority: Long? = null
  public var retry: Retry? = null
  public var signature: Signature? = null
  public var skip: Skip? = null
  public var timeoutInMinutes: Long? = null
  public var script: ScriptStep? = null
  public var build: Build? = null
  public var input: Input? = null
  public var continueOnFailure: Boolean? = null
  public var wait: Wait? = null
  public var waiter: Wait? = null

  public fun commands(command: String) {
    this.command = command
  }

  public fun commands(@Language("bash") vararg commands: String) {
    this.commands = Commands.multiple(commands.toList())
  }

  public fun branches(vararg branches: String) {
    this.branches = SimpleStringValue(branches.toList())
  }

  public fun artifacts(vararg paths: String) {
    artifactPaths = SimpleStringValue(paths.toList())
  }

  public fun notify(notification: Notification) {
    notify += notification
  }

  public fun softFail() {
    softFail = SoftFail(true)
  }

  public fun softFail(exitCode: Int) {
    softFail = SoftFail(listOf(SoftFailElement(ExitStatusUnion(exitCode.toLong()))))
  }

  public fun softFail(vararg exitCodes: Int) {
    softFail = SoftFail(exitCodes.map { SoftFailElement(ExitStatusUnion(it.toLong())) })
  }

  public fun trigger(trigger: String) {
    this.trigger = Trigger.StringValue(trigger)
  }

  public fun build(): CommandStep =
    CommandStep(
      allowDependencyFailure = allowDependencyFailure,
      block = block,
      blockedState = blockedState,
      branches = branch?.let(SimpleStringValue::invoke) ?: branches,
      dependsOn = dependsOn,
      fields = fields,
      id = id,
      identifier = identifier,
      stepIf = stepIf,
      key = key,
      label = label,
      name = name,
      prompt = prompt,
      type = type,
      agents = agents,
      artifactPaths = artifactPaths,
      cancelOnBuildFailing = cancelOnBuildFailing,
      //      command = command?.let { Commands(it) },
      commands = commands,
      concurrency = concurrency,
      concurrencyGroup = concurrencyGroup,
      concurrencyMethod = concurrencyMethod,
      env = env.takeUnless { it.isEmpty() },
      matrix = matrix,
      notify = notify.takeUnless { it.isEmpty() },
      parallelism = parallelism,
      plugins = plugins,
      priority = priority,
      retry = retry,
      signature = signature,
      skip = skip,
      softFail = softFail,
      timeoutInMinutes = timeoutInMinutes,
      script = script,
      async = async,
      build = build,
      trigger = trigger,
      input = input,
      continueOnFailure = continueOnFailure,
      wait = wait,
      waiter = waiter,
    )
}

@BuildkiteDsl
public class GroupBuilder(public val name: String, public var key: String? = null) :
  StepBuilder by StepBuilderImpl() {

  public fun build(): NestedBlockStepClass =
    NestedBlockStepClass(group = name, key = key, steps = steps)
}

public interface StepBuilder {
  public val steps: List<Step>

  public fun step(step: Step)

  public fun commandStep(block: CommandStepBuilder.() -> Unit): CommandStep

  public fun addStep(step: Step)

  public fun addStep(step: CommandStep) {
    addStep(Step(step))
  }
}

internal class StepBuilderImpl : StepBuilder {
  override val steps: MutableList<Step> = mutableListOf()

  override fun step(step: Step) {
    steps.add(step)
  }

  override fun commandStep(block: CommandStepBuilder.() -> Unit): CommandStep {
    val step = buildCommandStep(block)
    addStep(step)
    return step
  }

  override fun addStep(step: Step) {
    steps.add(step)
  }
}

public interface EnvOwner {
  public val env: Map<String, String>

  public fun env(vararg pairs: Pair<String, String>)

  public fun env(block: EnvBuilder.() -> Unit)
}

internal class EnvOwnerImpl : EnvOwner {
  override val env = mutableMapOf<String, String>()

  override fun env(vararg pairs: Pair<String, String>) {
    env.putAll(pairs)
  }

  override fun env(block: EnvBuilder.() -> Unit) {
    env += EnvBuilder().apply(block).build()
  }
}

public interface StepWithDependencies {
  public val dependsOn: DependsOn?

  public fun dependsOn(value: String)

  public fun dependsOn(vararg values: String): Unit =
    dependsOn(values.toList().map(DependsOnElement::invoke))

  public fun dependsOn(value: List<DependsOnElement>)

  public fun dependsOn(value: Keyable): Unit = dependsOn(requireKey(value))

  @Suppress("SpreadOperator")
  public fun dependsOn(vararg values: Keyable): Unit =
    dependsOn(*values.map { requireKey(it) }.toTypedArray())
}

internal class StepWithDependenciesImpl : StepWithDependencies {
  override var dependsOn: DependsOn? = null

  override fun dependsOn(value: String) {
    dependsOn = DependsOn.StringValue(value)
  }

  override fun dependsOn(value: List<DependsOnElement>) {
    dependsOn = DependsOn.UnionArrayValue(value)
  }
}

public fun StepWithDependencies.dependsOn(value: List<Keyable>): Unit =
  dependsOn(value.map { DependsOnElement.invoke(requireKey(it)) })

private fun requireKey(keyable: Keyable): String {
  return requireNotNull(keyable.key) { "Key for step must not be null to depend on it: $keyable" }
}

public fun jsonObject(block: JsonObjectBuilder.() -> Unit): JsonObject {
  return JsonObjectBuilder().apply(block).build()
}

public fun jsonArray(vararg elements: JsonElement): JsonArray {
  return JsonArray(elements.toList())
}

@BuildkiteDsl
public class JsonObjectBuilder {
  private val content = mutableMapOf<String, JsonElement>()

  public infix fun String.to(value: String) {
    content[this] = JsonPrimitive(value)
  }

  public infix fun String.to(value: Number) {
    content[this] = JsonPrimitive(value)
  }

  public infix fun String.to(value: Boolean) {
    content[this] = JsonPrimitive(value)
  }

  public infix fun String.to(value: JsonArray) {
    content[this] = value
  }

  public infix fun String.to(value: JsonObject) {
    content[this] = value
  }

  public fun build(): JsonObject {
    return JsonObject(content)
  }
}

@BuildkiteDsl
public class AutomaticElementBuilder {
  public var exitStatus: Long? = null
  public var limit: Long? = null
  public var signal: String? = null
  public var signalReason: SignalReason? = null

  public fun build(): AutomaticElement =
    AutomaticElement(
      exitStatus = exitStatus?.let { ExitStatusUnion(it) },
      limit = limit,
      signal = signal,
      signalReason = signalReason,
    )
}

public fun automaticElement(block: AutomaticElementBuilder.() -> Unit): AutomaticElement {
  val builder = AutomaticElementBuilder()
  builder.block()
  return builder.build()
}

@BuildkiteDsl
public class AutomaticBuilder {
  private val conditions = mutableListOf<AutomaticElement>()

  public fun condition(block: AutomaticElementBuilder.() -> Unit) {
    conditions.add(automaticElement(block))
  }

  public fun build(): Automatic = Automatic.AutomaticElementArrayValue(conditions)
}

@BuildkiteDsl
public class RetryBuilder {
  private var automatic: Automatic? = null
  private var manual: ManualUnion? = null

  public fun automatic(block: AutomaticBuilder.() -> Unit) {
    automatic = AutomaticBuilder().apply(block).build()
  }

  public fun manual(block: ManualClassBuilder.() -> Unit) {
    manual = ManualUnion.ManualClassValue(ManualClassBuilder().apply(block).build())
  }

  public fun manual(allowed: Boolean) {
    manual = ManualUnion.BoolValue(allowed)
  }

  public fun build(): Retry = Retry(automatic = automatic, manual = manual)
}

public fun retry(block: RetryBuilder.() -> Unit): Retry {
  val builder = RetryBuilder()
  builder.block()
  return builder.build()
}

@BuildkiteDsl
public class ManualClassBuilder {
  public var reason: String? = null
  public var allowed: Boolean? = null
  public var permitOnPassed: Boolean? = null

  public fun build(): ManualClass =
    ManualClass(reason = reason, allowed = allowed, permitOnPassed = permitOnPassed)
}
