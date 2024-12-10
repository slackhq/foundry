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
@file:UseSerializers(JsonObjectAsMapSerializer::class, JsonElementKamlSerializer::class)

package foundry.cli.buildkite

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/*
 * Generated with https://app.quicktype.io/ using
 * https://raw.githubusercontent.com/buildkite/pipeline-schema/main/schema.json.
 *
 * Modifications after:
 * - Convert sealed classes to sealed interfaces
 * - Convert sealed subtypes to value classes or data objects. This is important for the yaml to actually serialize
 *   correctly, otherwise there will be intermediate "value" levels.
 * - Add @file:UseSerializers for JsonObjectAsMapSerializer and JsonElementKamlSerializer so that we can serialize
 *   the json types.
 * - Change ExitStatusUnion.DoubleValue to a LongValue as exit codes aren't doubles.
 * - Add invoke operators for all sealed interfaces to make it easier to construct the data classes.
 * - Add GroupStep.WaitStepValue for inline Wait.
 * - Add GroupStep.BlockStepValue for inline BlockStep.
 * - Renames
 *   - `CommandStep` -> `ScriptStep`
 *   - `PurpleStepValue` -> `CommandStepValue`
 *   - `PurpleStep` -> `CommandStep`
 *   - `PurpleGithubCommitStatus` -> `GithubCommitStatus`
 *   - `PurpleGithubCheck` -> `GithubCheck`
 *   - `StickySlack` -> `SlackNotification`
 *   - `StickySlack.PurpleSlackValue` -> `StickySlack.Multi`
 *   - `StickySlack.StringValue` -> `StickySlack.Conversation`
 *   - `PurpleSlack` -> `MultiChannelMessage`
 *   - `PurpleBranches` -> `Branches`
 *   - `Coordinat` -> `GithubNotification`
 *   - `PurpleType` -> `StepType`
 *   - `NotifyElement` -> `Notification`
 *   - `NotifyElement.EnumValue` -> `NotifyElement.GitHub`
 *   - `NotifyClass` -> `ExternalNotification`
 *   - `CoordinateClass` -> `Pipeline`
 *
 * - Consolidations
 *   - `IndigoSlack` -> X (SlackNotification)
 *   - `IndecentSlack` -> X (GithubCommitStatus)
 *   - `FluffySlack` -> X (MultiChannelMessage)
 *   - `TentacledSlack` -> X (MultiChannelMessage)
 *   - `FluffyGithubCheck` -> X (GithubCheck)
 *   - `FluffyGithubCommitStatus` -> X (GithubCommitStatus)
 *   - `TentacledGithubCheck` -> X (GithubCheck)
 *   - `TentacledGithubCommitStatus` -> X (GithubCommitStatus)
 *   - `ScriptNotify.EnumValue` -> X (Notification)
 *   - `ScriptNotify.PurpleNotifyValue` -> X (Notification)
 *   - `PurpleNotify` -> X (ExternalNotification)`
 *   - `NestedBlockStepNotify` -> X (Notification)`
 *   - `FluffyNotifyValue` -> X (ExternalNotification)`
 */

@Serializable
public data class Pipeline(
  /** A list of steps */
  val steps: List<GroupStep>,
  val agents: Agents? = null,
  val env: Map<String, String>? = null,
  val notify: List<Notification>? = null,
)

@Serializable
public sealed interface Agents {
  @JvmInline
  @Serializable
  public value class AnythingMapValue(public val value: JsonObject) : Agents

  @JvmInline
  @Serializable
  public value class StringArrayValue(public val value: List<String>) : Agents

  public companion object {
    public operator fun invoke(value: JsonObject): Agents = AnythingMapValue(value)

    public operator fun invoke(value: List<String>): Agents = StringArrayValue(value)
  }
}

/** Array of notification options for this step */
@Serializable
public sealed interface Notification {
  @JvmInline
  @Serializable
  public value class GitHub(public val value: GithubNotification) : Notification

  @JvmInline
  @Serializable
  public value class External(public val value: ExternalNotification) : Notification

  public companion object {
    public operator fun invoke(value: GithubNotification): Notification = GitHub(value)

    public operator fun invoke(value: ExternalNotification): Notification = External(value)
  }
}

@Serializable
public data class ExternalNotification(
  val email: String? = null,
  @SerialName("if") val notifyIf: String? = null,
  @SerialName("basecamp_campfire") val basecampCampfire: String? = null,
  val slack: SlackNotification? = null,
  val webhook: String? = null,
  @SerialName("pagerduty_change_event") val pagerdutyChangeEvent: String? = null,
  @SerialName("github_commit_status") val githubCommitStatus: GithubCommitStatus? = null,
  @SerialName("github_check") val githubCheck: GithubCheck? = null,
)

@Serializable
public data class GithubCheck(
  /** GitHub commit status name */
  val context: String? = null
)

@Serializable
public data class GithubCommitStatus(
  /** GitHub commit status name */
  val context: String? = null
)

@Serializable
public sealed interface SlackNotification {
  @Serializable
  @JvmInline
  public value class Multi(public val value: MultiChannelMessage) : SlackNotification

  @Serializable
  @JvmInline
  public value class Conversation(public val value: String) : SlackNotification
}

@Serializable
public data class MultiChannelMessage(
  val channels: List<String>? = null,
  val message: String? = null,
)

@Serializable
public enum class GithubNotification(public val value: String) {
  @SerialName("github_check") GithubCheck("github_check"),
  @SerialName("github_commit_status") GithubCommitStatus("github_commit_status"),
}

@Serializable
public sealed interface GroupStep : Keyable {
  @Serializable
  @JvmInline
  public value class AnythingArrayValue(public val value: JsonArray) : GroupStep {
    override val key: String?
      get() = null
  }

  @Serializable
  @JvmInline
  public value class BoolValue(public val value: Boolean) : GroupStep {
    override val key: String?
      get() = null
  }

  @Serializable
  @JvmInline
  public value class DoubleValue(public val value: Double) : GroupStep {
    override val key: String?
      get() = null
  }

  @Serializable
  @JvmInline
  public value class IntegerValue(public val value: Long) : GroupStep {
    override val key: String?
      get() = null
  }

  @Serializable
  @JvmInline
  public value class NestedBlockStepClassValue(public val value: NestedBlockStepClass) :
    GroupStep, Keyable by value

  @Serializable
  @JvmInline
  public value class WaitStepValue(public val value: Wait) : GroupStep, Keyable by value

  @Serializable
  @JvmInline
  public value class BlockStepValue(public val value: BlockStep) : GroupStep, Keyable by value

  @Serializable
  @JvmInline
  public value class CommandStepValue(public val value: CommandStep) : GroupStep, Keyable by value

  @Serializable
  @JvmInline
  public value class StringValue(public val value: String) : GroupStep {
    override val key: String?
      get() = null
  }

  @Serializable
  public data object NullValue : GroupStep {
    override val key: String?
      get() = null
  }

  public companion object {
    public operator fun invoke(value: JsonArray): GroupStep = AnythingArrayValue(value)

    public operator fun invoke(value: Boolean): GroupStep = BoolValue(value)

    public operator fun invoke(value: Double): GroupStep = DoubleValue(value)

    public operator fun invoke(value: Long): GroupStep = IntegerValue(value)

    public operator fun invoke(value: NestedBlockStepClass): GroupStep =
      NestedBlockStepClassValue(value)

    public operator fun invoke(value: String): GroupStep = StringValue(value)

    public operator fun invoke(value: Wait): GroupStep = WaitStepValue(value)

    public operator fun invoke(value: BlockStep): GroupStep = BlockStepValue(value)

    public operator fun invoke(value: CommandStep): GroupStep = CommandStepValue(value)
  }
}

/** Waits for previous steps to pass before continuing */
@Serializable
public data class NestedBlockStepClass(
  @SerialName("allow_dependency_failure") val allowDependencyFailure: Boolean? = null,

  /** The label of the block step */
  val block: Block? = null,

  /** The state that the build is set to when the build is blocked by this block step */
  @SerialName("blocked_state") val blockedState: BlockedState? = null,
  val branches: SimpleStringValue? = null,
  @SerialName("depends_on") val dependsOn: DependsOn? = null,
  val fields: List<FieldElement>? = null,
  val id: String? = null,
  val identifier: String? = null,
  @SerialName("if") val stepIf: String? = null,
  override val key: String? = null,
  val label: String? = null,
  val name: String? = null,
  val prompt: String? = null,
  val type: NestedBlockStepType? = null,

  /** The label of the input step */
  val input: Input? = null,
  val agents: Agents? = null,

  /** The glob path/s of artifacts to upload once this step has finished running */
  @SerialName("artifact_paths") val artifactPaths: SimpleStringValue? = null,
  @SerialName("cancel_on_build_failing") val cancelOnBuildFailing: Boolean? = null,

  /** The commands to run on the agent */
  val command: Commands? = null,

  /** The commands to run on the agent */
  val commands: Commands? = null,

  /**
   * The maximum number of jobs created from this step that are allowed to run at the same time. If
   * you use this attribute, you must also define concurrency_group.
   */
  val concurrency: Long? = null,

  /**
   * A unique name for the concurrency group that you are creating with the concurrency attribute
   */
  @SerialName("concurrency_group") val concurrencyGroup: String? = null,

  /**
   * Control command order, allowed values are 'ordered' (default) and 'eager'. If you use this
   * attribute, you must also define concurrency_group and concurrency.
   */
  @SerialName("concurrency_method") val concurrencyMethod: ConcurrencyMethod? = null,
  val env: Map<String, String>? = null,
  val matrix: MatrixUnion? = null,

  /** Array of notification options for this step */
  val notify: List<Notification>? = null,

  /** The number of parallel jobs that will be created based on this step */
  val parallelism: Long? = null,
  val plugins: Plugins? = null,

  /** Priority of the job, higher priorities are assigned to agents */
  val priority: Long? = null,

  /** The conditions for retrying this step. */
  val retry: Retry? = null,

  /** The signature of the command step, generally injected by agents at pipeline upload */
  val signature: Signature? = null,
  val skip: Skip? = null,
  @SerialName("soft_fail") val softFail: SoftFail? = null,

  /** The number of minutes to time out a job */
  @SerialName("timeout_in_minutes") val timeoutInMinutes: Long? = null,
  val script: ScriptStep? = null,

  /** Continue to the next steps, even if the previous group of steps fail */
  @SerialName("continue_on_failure") val continueOnFailure: Boolean? = null,

  /** Waits for previous steps to pass before continuing */
  val wait: Wait? = null,
  val waiter: Wait? = null,

  /** Whether to continue the build without waiting for the triggered step to complete */
  val async: Boolean? = null,

  /** Properties of the build that will be created when the step is triggered */
  val build: Build? = null,

  /** The slug of the pipeline to create a build */
  val trigger: Trigger? = null,

  /** The name to give to this group of steps */
  val group: String? = null,

  /** A list of steps */
  val steps: List<Step>? = null,
) : Keyable

/**
 * - Which branches will include this step in their builds
 * - The value of the option(s) that will be pre-selected in the dropdown
 * - The glob path/s of artifacts to upload once this step has finished running
 * - The commands to run on the agent
 */
@Serializable
public sealed interface SimpleStringValue {
  @Serializable
  @JvmInline
  public value class ListValue(public val value: List<String>) : SimpleStringValue

  @Serializable
  @JvmInline
  public value class SingleValue(public val value: String) : SimpleStringValue

  public companion object {
    public operator fun invoke(value: List<String>): SimpleStringValue = ListValue(value)

    public operator fun invoke(value: String): SimpleStringValue = SingleValue(value)

    public operator fun invoke(vararg values: String): SimpleStringValue =
      ListValue(values.toList())
  }
}

@Serializable
public sealed interface Block {
  @Serializable @JvmInline public value class BlockStepValue(public val value: BlockStep) : Block

  @Serializable @JvmInline public value class StringValue(public val value: String) : Block
}

@Serializable
public data class BlockStep(
  @SerialName("allow_dependency_failure") val allowDependencyFailure: Boolean? = null,

  /** The label of the block step */
  val block: String? = null,

  /** The state that the build is set to when the build is blocked by this block step */
  @SerialName("blocked_state") val blockedState: BlockedState? = null,
  val branches: SimpleStringValue? = null,
  @SerialName("depends_on") val dependsOn: DependsOn? = null,
  val fields: List<FieldElement>? = null,
  val id: String? = null,
  val identifier: String? = null,
  @SerialName("if") val blockStepIf: String? = null,
  override val key: String? = null,
  val label: String? = null,
  val name: String? = null,
  val prompt: String? = null,
  val type: BlockType? = null,
) : Keyable

/** The state that the build is set to when the build is blocked by this block step */
@Serializable
public enum class BlockedState(public val value: String) {
  @SerialName("failed") Failed("failed"),
  @SerialName("passed") Passed("passed"),
  @SerialName("running") Running("running"),
}

/** The step keys for a step to depend on */
@Serializable
public sealed interface DependsOn {
  @Serializable @JvmInline public value class StringValue(public val value: String) : DependsOn

  @Serializable
  @JvmInline
  public value class UnionArrayValue(public val value: List<DependsOnElement>) : DependsOn

  @Serializable public data object NullValue : DependsOn

  public companion object {
    public operator fun invoke(value: String): DependsOn = StringValue(value)

    public operator fun invoke(vararg values: String): DependsOn =
      invoke(values.toList().map(DependsOnElement::invoke))

    public operator fun invoke(value: List<DependsOnElement>): DependsOn = UnionArrayValue(value)

    public operator fun invoke(value: Keyable): DependsOn = invoke(requireKey(value))

    @Suppress("SpreadOperator")
    public operator fun invoke(vararg values: Keyable): DependsOn =
      invoke(*values.map { requireKey(it) }.toTypedArray())

    @JvmName("invokeKeyableList")
    public operator fun invoke(value: List<Keyable>): DependsOn =
      invoke(value.map { DependsOnElement.invoke(requireKey(it)) })

    private fun requireKey(keyable: Keyable): String {
      return requireNotNull(keyable.key) {
        "Key for step must not be null to depend on it: $keyable"
      }
    }
  }
}

@Serializable
public sealed interface DependsOnElement {
  @Serializable
  @JvmInline
  public value class DependsOnClassValue(public val value: DependsOnClass) : DependsOnElement

  @Serializable
  @JvmInline
  public value class StringValue(public val value: String) : DependsOnElement

  public companion object {
    public operator fun invoke(value: DependsOnClass): DependsOnElement = DependsOnClassValue(value)

    public operator fun invoke(value: String): DependsOnElement = StringValue(value)
  }
}

@Serializable
public data class DependsOnClass(
  @SerialName("allow_failure") val allowFailure: Boolean? = null,
  val step: String? = null,
)

/** A list of input fields required to be filled out before unblocking the step */
@Serializable
public data class FieldElement(
  /**
   * The value that is pre-filled in the text field
   *
   * The value of the option(s) that will be pre-selected in the dropdown
   */
  val default: SimpleStringValue? = null,

  /** The explanatory text that is shown after the label */
  val hint: String? = null,

  /** The meta-data key that stores the field's input */
  val key: String,

  /** Whether the field is required for form submission */
  val required: Boolean? = null,

  /** The text input name */
  val text: String? = null,

  /** Whether more than one option may be selected */
  val multiple: Boolean? = null,
  val options: List<Option>? = null,

  /** The text input name */
  val select: String? = null,
)

@Serializable
public data class Option(
  /** The text displayed directly under the select field’s label */
  val hint: String? = null,

  /** The text displayed on the select list item */
  val label: String,

  /** Whether the field is required for form submission */
  val required: Boolean? = null,

  /** The value to be stored as meta-data */
  val value: String,
)

@Serializable
public enum class BlockType(public val value: String) {
  @SerialName("block") Block("block")
}

/** Properties of the build that will be created when the step is triggered */
@Serializable
public data class Build(
  /** The branch for the build */
  val branch: String? = null,

  /** The commit hash for the build */
  val commit: String? = null,
  val env: Map<String, String>? = null,
  val label: String? = null,

  /** The message for the build (supports emoji) */
  val message: String? = null,

  /** Meta-data for the build */
  @SerialName("meta_data") val metaData: JsonObject? = null,
  val name: String? = null,

  /** The slug of the pipeline to create a build */
  val trigger: String? = null,
  val type: BuildType? = null,
)

@Serializable
public enum class BuildType(public val value: String) {
  @SerialName("trigger") Trigger("trigger")
}

@Serializable
public sealed interface Commands {
  @Serializable
  @JvmInline
  public value class ScriptStepValue(public val value: ScriptStep) : Commands

  @Serializable
  @JvmInline
  public value class StringArrayValue(public val value: List<String>) : Commands

  @Serializable @JvmInline public value class StringValue(public val value: String) : Commands

  public companion object {
    public fun single(value: String): Commands = StringValue(value)

    public fun multiple(value: List<String>): Commands = StringArrayValue(value)

    public fun multiple(vararg values: String): Commands = StringArrayValue(values.toList())

    public fun step(value: ScriptStep): Commands = ScriptStepValue(value)
  }
}

@Serializable
public data class ScriptStep(
  val agents: Agents? = null,
  @SerialName("allow_dependency_failure") val allowDependencyFailure: Boolean? = null,

  /** The glob path/s of artifacts to upload once this step has finished running */
  @SerialName("artifact_paths") val artifactPaths: SimpleStringValue? = null,
  val branches: SimpleStringValue? = null,
  @SerialName("cancel_on_build_failing") val cancelOnBuildFailing: Boolean? = null,

  /** The commands to run on the agent */
  val command: SimpleStringValue? = null,

  /** The commands to run on the agent */
  val commands: SimpleStringValue? = null,

  /**
   * The maximum number of jobs created from this step that are allowed to run at the same time. If
   * you use this attribute, you must also define concurrency_group.
   */
  val concurrency: Long? = null,

  /**
   * A unique name for the concurrency group that you are creating with the concurrency attribute
   */
  @SerialName("concurrency_group") val concurrencyGroup: String? = null,

  /**
   * Control command order, allowed values are 'ordered' (default) and 'eager'. If you use this
   * attribute, you must also define concurrency_group and concurrency.
   */
  @SerialName("concurrency_method") val concurrencyMethod: ConcurrencyMethod? = null,
  @SerialName("depends_on") val dependsOn: DependsOn? = null,
  val env: Map<String, String>? = null,
  val id: String? = null,
  val identifier: String? = null,
  @SerialName("if") val commandStepIf: String? = null,
  override val key: String? = null,
  val label: String? = null,
  val matrix: MatrixUnion? = null,
  val name: String? = null,

  /** Array of notification options for this step */
  val notify: List<Notification>? = null,

  /** The number of parallel jobs that will be created based on this step */
  val parallelism: Long? = null,
  val plugins: Plugins? = null,

  /** Priority of the job, higher priorities are assigned to agents */
  val priority: Long? = null,

  /** The conditions for retrying this step. */
  val retry: Retry? = null,

  /** The signature of the command step, generally injected by agents at pipeline upload */
  val signature: Signature? = null,
  val skip: Skip? = null,
  @SerialName("soft_fail") val softFail: SoftFail? = null,

  /** The number of minutes to time out a job */
  @SerialName("timeout_in_minutes") val timeoutInMinutes: Long? = null,
  val type: ScriptType? = null,
) : Keyable

/**
 * Control command order, allowed values are 'ordered' (default) and 'eager'. If you use this
 * attribute, you must also define concurrency_group and concurrency.
 */
@Serializable
public enum class ConcurrencyMethod(public val value: String) {
  @SerialName("eager") Eager("eager"),
  @SerialName("ordered") Ordered("ordered"),
}

@Serializable
public sealed interface MatrixUnion {
  @Serializable
  @JvmInline
  public value class MatrixClassValue(public val value: MatrixClass) : MatrixUnion

  @Serializable
  @JvmInline
  public value class UnionArrayValue(public val value: List<MatrixElement>) : MatrixUnion
}

/**
 * List of elements for simple single-dimension Build Matrix
 *
 * List of existing or new elements for single-dimension Build Matrix
 *
 * List of elements for single-dimension Build Matrix
 *
 * List of elements for this Build Matrix dimension
 */
@Serializable
public sealed interface MatrixElement {
  @Serializable @JvmInline public value class BoolValue(public val value: Boolean) : MatrixElement

  @Serializable @JvmInline public value class IntegerValue(public val value: Long) : MatrixElement

  @Serializable @JvmInline public value class StringValue(public val value: String) : MatrixElement
}

/** Configuration for multi-dimension Build Matrix */
@Serializable
public data class MatrixClass(
  /** List of Build Matrix adjustments */
  val adjustments: List<Adjustment>? = null,
  val setup: Setup,
)

/** An adjustment to a Build Matrix */
@Serializable
public data class Adjustment(
  val skip: Skip? = null,
  @SerialName("soft_fail") val softFail: SoftFail? = null,
  val with: With,
)

/** Whether this step should be skipped. You can specify a reason for using a string. */
@Serializable
public sealed interface Skip {
  @Serializable @JvmInline public value class BoolValue(public val value: Boolean) : Skip

  @Serializable @JvmInline public value class StringValue(public val value: String) : Skip
}

/** The conditions for marking the step as a soft-fail. */
@Serializable
public sealed interface SoftFail {
  @Serializable @JvmInline public value class BoolValue(public val value: Boolean) : SoftFail

  @Serializable
  @JvmInline
  public value class Multiple(public val value: List<SoftFailElement>) : SoftFail

  public companion object {
    public operator fun invoke(value: Boolean): SoftFail = BoolValue(value)

    public operator fun invoke(value: List<SoftFailElement>): SoftFail = Multiple(value)
  }
}

@Serializable
public data class SoftFailElement(
  /** The exit status number that will cause this job to soft-fail */
  @SerialName("exit_status") val exitStatus: ExitStatusUnion? = null
)

@Serializable
public sealed interface ExitStatusUnion {
  @Serializable @JvmInline public value class LongValue(public val value: Long) : ExitStatusUnion

  @Serializable
  @JvmInline
  public value class EnumValue(public val value: ExitStatusEnum) : ExitStatusUnion

  public companion object {
    public operator fun invoke(value: Long): ExitStatusUnion = LongValue(value)

    public operator fun invoke(value: ExitStatusEnum): ExitStatusUnion = EnumValue(value)
  }
}

@Serializable
public enum class ExitStatusEnum(public val value: String) {
  @SerialName("*") Empty("*")
}

@Serializable
public sealed interface With {
  @Serializable
  @JvmInline
  public value class StringMapValue(public val value: Map<String, String>) : With

  @Serializable
  @JvmInline
  public value class UnionArrayValue(public val value: List<MatrixElement>) : With
}

@Serializable
public sealed interface Setup {
  @Serializable
  @JvmInline
  public value class UnionArrayMapValue(public val value: Map<String, List<MatrixElement>>) : Setup

  @Serializable
  @JvmInline
  public value class UnionArrayValue(public val value: List<MatrixElement>) : Setup
}

@Serializable
public sealed interface Plugins {
  @Serializable
  @JvmInline
  public value class AnythingMapValue(public val value: JsonObject) : Plugins

  @Serializable
  @JvmInline
  public value class UnionArrayValue(public val value: List<Plugin>) : Plugins

  public companion object {
    public operator fun invoke(value: JsonObject): Plugins = AnythingMapValue(value)

    public operator fun invoke(value: List<Plugin>): Plugins = UnionArrayValue(value)
  }
}

/** Array of plugins for this step */
@Serializable
public sealed interface Plugin {
  @Serializable
  @JvmInline
  public value class AnythingMapValue(public val value: JsonObject) : Plugin

  @Serializable @JvmInline public value class StringValue(public val value: String) : Plugin

  public companion object {
    public operator fun invoke(value: JsonObject): Plugin = AnythingMapValue(value)

    public operator fun invoke(value: String): Plugin = StringValue(value)
  }
}

/** The conditions for retrying this step. */
@Serializable
public data class Retry(
  /**
   * Whether to allow a job to retry automatically. If set to true, the retry conditions are set to
   * the default value.
   */
  val automatic: Automatic? = null,

  /** Whether to allow a job to be retried manually */
  val manual: ManualUnion? = null,
)

/**
 * Whether to allow a job to retry automatically. If set to true, the retry conditions are set to
 * the default value.
 */
@Serializable
public sealed interface Automatic {
  @Serializable
  @JvmInline
  public value class AutomaticElementArrayValue(public val value: List<AutomaticElement>) :
    Automatic

  @Serializable
  @JvmInline
  public value class AutomaticElementValue(public val value: AutomaticElement) : Automatic

  @Serializable @JvmInline public value class BoolValue(public val value: Boolean) : Automatic
}

@Serializable
public data class AutomaticElement(
  /** The exit status number that will cause this job to retry */
  @SerialName("exit_status") val exitStatus: ExitStatusUnion? = null,

  /** The number of times this job can be retried */
  val limit: Long? = null,

  /** The exit signal, if any, that may be retried */
  val signal: String? = null,

  /** The exit signal reason, if any, that may be retried */
  @SerialName("signal_reason") val signalReason: SignalReason? = null,
)

/** The exit signal reason, if any, that may be retried */
@Serializable
public enum class SignalReason(public val value: String) {
  @SerialName("agent_refused") AgentRefused("agent_refused"),
  @SerialName("agent_stop") AgentStop("agent_stop"),
  @SerialName("cancel") Cancel("cancel"),
  @SerialName("*") Empty("*"),
  @SerialName("none") None("none"),
  @SerialName("process_run_error") ProcessRunError("process_run_error"),
}

/** Whether to allow a job to be retried manually */
@Serializable
public sealed interface ManualUnion {
  @Serializable @JvmInline public value class BoolValue(public val value: Boolean) : ManualUnion

  @Serializable
  @JvmInline
  public value class ManualClassValue(public val value: ManualClass) : ManualUnion
}

@Serializable
public data class ManualClass(
  /** Whether or not this job can be retried manually */
  val allowed: Boolean? = null,

  /** Whether or not this job can be retried after it has passed */
  @SerialName("permit_on_passed") val permitOnPassed: Boolean? = null,

  /**
   * A string that will be displayed in a tooltip on the Retry button in Buildkite. This will only
   * be displayed if the allowed attribute is set to false.
   */
  val reason: String? = null,
)

/** The signature of the command step, generally injected by agents at pipeline upload */
@Serializable
public data class Signature(
  /** The algorithm used to generate the signature */
  val algorithm: String? = null,

  /** The fields that were signed to form the signature value */
  @SerialName("signed_fields") val signedFields: List<String>? = null,

  /** The signature value, a JWS compact signature with a detached body */
  val value: String? = null,
)

@Serializable
public enum class ScriptType(public val value: String) {
  @SerialName("command") Command("command"),
  @SerialName("commands") Commands("commands"),
  @SerialName("script") Script("script"),
}

@Serializable
public sealed interface Input {
  @Serializable @JvmInline public value class InputStepValue(public val value: InputStep) : Input

  @Serializable @JvmInline public value class StringValue(public val value: String) : Input
}

@Serializable
public data class InputStep(
  @SerialName("allow_dependency_failure") val allowDependencyFailure: Boolean? = null,
  val branches: SimpleStringValue? = null,
  @SerialName("depends_on") val dependsOn: DependsOn? = null,
  val fields: List<FieldElement>? = null,
  val id: String? = null,
  val identifier: String? = null,
  @SerialName("if") val inputStepIf: String? = null,

  /** The label of the input step */
  val input: String? = null,
  override val key: String? = null,
  val label: String? = null,
  val name: String? = null,
  val prompt: String? = null,
  val type: InputType? = null,
) : Keyable

@Serializable
public enum class InputType(public val value: String) {
  @SerialName("input") Input("input")
}

@Serializable
public sealed interface Step : Keyable {
  @Serializable
  @JvmInline
  public value class EnumValue(public val value: StringStep) : Step {
    override val key: String?
      get() = null
  }

  @Serializable
  @JvmInline
  public value class CommandStepValue(public val value: CommandStep) : Step, Keyable by value

  @Serializable
  @JvmInline
  public value class InputStepValue(public val value: InputStep) : Step, Keyable by value

  public companion object {
    public operator fun invoke(value: StringStep): Step = EnumValue(value)

    public operator fun invoke(value: CommandStep): Step = CommandStepValue(value)

    public operator fun invoke(value: InputStep): Step = InputStepValue(value)
  }
}

/** Waits for previous steps to pass before continuing */
@Serializable
public data class CommandStep(
  @SerialName("allow_dependency_failure") val allowDependencyFailure: Boolean? = null,

  /** The label of the block step */
  val block: String? = null,

  /** The state that the build is set to when the build is blocked by this block step */
  @SerialName("blocked_state") val blockedState: BlockedState? = null,
  val branches: SimpleStringValue? = null,
  @SerialName("depends_on") val dependsOn: DependsOn? = null,
  val fields: List<FieldElement>? = null,
  val id: String? = null,
  val identifier: String? = null,
  @SerialName("if") val stepIf: String? = null,
  override val key: String? = null,
  val label: String? = null,
  val name: String? = null,
  val prompt: String? = null,
  val type: StepType? = null,
  val agents: Agents? = null,

  /** The glob path/s of artifacts to upload once this step has finished running */
  @SerialName("artifact_paths") val artifactPaths: SimpleStringValue? = null,
  @SerialName("cancel_on_build_failing") val cancelOnBuildFailing: Boolean? = null,

  /** The commands to run on the agent */
  val command: Commands? = null,

  /** The commands to run on the agent */
  val commands: Commands? = null,

  /**
   * The maximum number of jobs created from this step that are allowed to run at the same time. If
   * you use this attribute, you must also define concurrency_group.
   */
  val concurrency: Long? = null,

  /**
   * A unique name for the concurrency group that you are creating with the concurrency attribute
   */
  @SerialName("concurrency_group") val concurrencyGroup: String? = null,

  /**
   * Control command order, allowed values are 'ordered' (default) and 'eager'. If you use this
   * attribute, you must also define concurrency_group and concurrency.
   */
  @SerialName("concurrency_method") val concurrencyMethod: ConcurrencyMethod? = null,
  val env: Map<String, String>? = null,
  val matrix: MatrixUnion? = null,

  /** Array of notification options for this step */
  val notify: List<Notification>? = null,

  /** The number of parallel jobs that will be created based on this step */
  val parallelism: Long? = null,
  val plugins: Plugins? = null,

  /** Priority of the job, higher priorities are assigned to agents */
  val priority: Long? = null,

  /** The conditions for retrying this step. */
  val retry: Retry? = null,

  /** The signature of the command step, generally injected by agents at pipeline upload */
  val signature: Signature? = null,
  val skip: Skip? = null,
  @SerialName("soft_fail") val softFail: SoftFail? = null,

  /** The number of minutes to time out a job */
  @SerialName("timeout_in_minutes") val timeoutInMinutes: Long? = null,
  val script: ScriptStep? = null,

  /** Whether to continue the build without waiting for the triggered step to complete */
  val async: Boolean? = null,

  /** Properties of the build that will be created when the step is triggered */
  val build: Build? = null,

  /** The slug of the pipeline to create a build */
  val trigger: Trigger? = null,

  /** The label of the input step */
  val input: Input? = null,

  /** Continue to the next steps, even if the previous group of steps fail */
  @SerialName("continue_on_failure") val continueOnFailure: Boolean? = null,

  /** Waits for previous steps to pass before continuing */
  val wait: Wait? = null,
  val waiter: Wait? = null,
) : Keyable

@Serializable
public sealed interface Trigger : Keyable {
  @Serializable
  @JvmInline
  public value class StringValue(public val value: String) : Trigger {
    override val key: String?
      get() = null
  }

  @Serializable
  @JvmInline
  public value class TriggerStepValue(public val value: TriggerStep) : Trigger, Keyable by value
}

@Serializable
public data class TriggerStep(
  @SerialName("allow_dependency_failure") val allowDependencyFailure: Boolean? = null,

  /** Whether to continue the build without waiting for the triggered step to complete */
  val async: Boolean? = null,
  val branches: SimpleStringValue? = null,

  /** Properties of the build that will be created when the step is triggered */
  val build: Build? = null,
  @SerialName("depends_on") val dependsOn: DependsOn? = null,
  val id: String? = null,
  val identifier: String? = null,
  @SerialName("if") val triggerStepIf: String? = null,
  override val key: String? = null,
  val label: String? = null,
  val name: String? = null,
  val skip: Skip? = null,
  @SerialName("soft_fail") val softFail: SoftFail? = null,

  /** The slug of the pipeline to create a build */
  val trigger: String? = null,
  val type: BuildType? = null,
) : Keyable

@Serializable
public enum class StepType(public val value: String) {
  @SerialName("block") Block("block"),
  @SerialName("command") Command("command"),
  @SerialName("commands") Commands("commands"),
  @SerialName("input") Input("input"),
  @SerialName("script") Script("script"),
  @SerialName("trigger") Trigger("trigger"),
  @SerialName("wait") Wait("wait"),
  @SerialName("waiter") Waiter("waiter"),
}

@Serializable
public sealed interface Wait : Keyable {
  @Serializable
  @JvmInline
  public value class StringValue(public val value: String) : Wait {
    override val key: String?
      get() = null
  }

  @Serializable
  @JvmInline
  public value class WaitStepValue(public val value: WaitStep) : Wait, Keyable by value

  @Serializable
  public data object NullValue : Wait {
    override val key: String?
      get() = null
  }
}

/** Waits for previous steps to pass before continuing */
@Serializable
public data class WaitStep(

  /** Waits for previous steps to pass before continuing */
  val wait: String? = null,
  @SerialName("allow_dependency_failure") val allowDependencyFailure: Boolean? = null,
  /** Continue to the next steps, even if the previous group of steps fail */
  @SerialName("continue_on_failure") val continueOnFailure: Boolean? = null,
  @SerialName("depends_on") val dependsOn: DependsOn? = null,
  val id: String? = null,
  val identifier: String? = null,
  @SerialName("if") val waitStepIf: String? = null,
  override val key: String? = null,
  val type: WaitType? = null,
  val waiter: String? = null,
) : Keyable

@Serializable
public enum class WaitType(public val value: String) {
  @SerialName("wait") Wait("wait"),
  @SerialName("waiter") Waiter("waiter"),
}

/**
 * Pauses the execution of a build and waits on a user to unblock it
 *
 * Waits for previous steps to pass before continuing
 */
@Serializable
public enum class StringStep(public val value: String) {
  @SerialName("block") Block("block"),
  @SerialName("input") Input("input"),
  @SerialName("wait") Wait("wait"),
  @SerialName("waiter") Waiter("waiter"),
}

@Serializable
public enum class NestedBlockStepType(public val value: String) {
  @SerialName("block") Block("block"),
  @SerialName("command") Command("command"),
  @SerialName("commands") Commands("commands"),
  @SerialName("group") Group("group"),
  @SerialName("input") Input("input"),
  @SerialName("script") Script("script"),
  @SerialName("trigger") Trigger("trigger"),
  @SerialName("wait") Wait("wait"),
  @SerialName("waiter") Waiter("waiter"),
}
