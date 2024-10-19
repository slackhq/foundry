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
@file:Suppress("invisible_reference", "invisible_member")
@file:OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)

package foundry.cli.buildkite

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.descriptors.mapSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonLiteral
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.internal.JsonDecodingException

internal object JsonObjectAsMapSerializer : KSerializer<JsonObject> {
  @OptIn(ExperimentalSerializationApi::class)
  override val descriptor: SerialDescriptor =
    buildClassSerialDescriptor("JsonObjectAsMap") {
      element(
        "properties",
        mapSerialDescriptor(String.serializer().descriptor, JsonElementKamlSerializer.descriptor),
      )
    }

  override fun serialize(encoder: Encoder, value: JsonObject) {
    val map = value.mapValues { it.value }
    encoder.encodeSerializableValue(
      MapSerializer(String.serializer(), JsonElementKamlSerializer),
      map,
    )
  }

  override fun deserialize(decoder: Decoder): JsonObject {
    throw UnsupportedOperationException("Deserialization not supported")
  }
}

/**
 * Serializer object providing [SerializationStrategy] and [DeserializationStrategy] for
 * [JsonElement]. It can only be used by with [Json] format and its input ([JsonDecoder] and
 * [JsonEncoder]). Currently, this hierarchy has no guarantees on descriptor content.
 *
 * Example usage:
 * ```
 * val string = Json.encodeToString(JsonElementSerializer, json { "key" to 1.0 })
 * val literal = Json.decodeFromString(JsonElementSerializer, string)
 * assertEquals(JsonObject(mapOf("key" to JsonLiteral(1.0))), literal)
 * ```
 */
@PublishedApi
internal object JsonElementKamlSerializer : KSerializer<JsonElement> {
  override val descriptor: SerialDescriptor =
    buildSerialDescriptor("kotlinx.serialization.json.JsonElement", PolymorphicKind.SEALED) {
      // Resolve cyclic dependency in descriptors by late binding
      element("JsonPrimitive", defer { JsonPrimitiveSerializer.descriptor })
      element("JsonNull", defer { JsonNullSerializer.descriptor })
      element("JsonLiteral", defer { JsonLiteralSerializer.descriptor })
      element("JsonObject", defer { JsonObjectSerializer.descriptor })
      element("JsonArray", defer { JsonArraySerializer.descriptor })
    }

  override fun serialize(encoder: Encoder, value: JsonElement) {
    when (value) {
      is JsonPrimitive -> encoder.encodeSerializableValue(JsonPrimitiveSerializer, value)
      is JsonObject -> encoder.encodeSerializableValue(JsonObjectSerializer, value)
      is JsonArray -> encoder.encodeSerializableValue(JsonArraySerializer, value)
    }
  }

  override fun deserialize(decoder: Decoder): JsonElement {
    val input = decoder.asJsonDecoder()
    return input.decodeJsonElement()
  }
}

/**
 * Serializer object providing [SerializationStrategy] and [DeserializationStrategy] for
 * [JsonPrimitive]. It can only be used by with [Json] format an its input ([JsonDecoder] and
 * [JsonEncoder]).
 */
@PublishedApi
internal object JsonPrimitiveSerializer : KSerializer<JsonPrimitive> {
  override val descriptor: SerialDescriptor =
    buildSerialDescriptor("kotlinx.serialization.json.JsonPrimitive", PrimitiveKind.STRING)

  override fun serialize(encoder: Encoder, value: JsonPrimitive) {
    return if (value is JsonNull) {
      encoder.encodeSerializableValue(JsonNullSerializer, JsonNull)
    } else {
      encoder.encodeSerializableValue(JsonLiteralSerializer, value as JsonLiteral)
    }
  }

  override fun deserialize(decoder: Decoder): JsonPrimitive {
    val result = decoder.asJsonDecoder().decodeJsonElement()
    if (result !is JsonPrimitive) {
      throw JsonDecodingException(
        -1,
        "Unexpected JSON element, expected JsonPrimitive, had ${result::class}",
        result.toString(),
      )
    }
    return result
  }
}

/**
 * Serializer object providing [SerializationStrategy] and [DeserializationStrategy] for [JsonNull].
 * It can only be used by with [Json] format an its input ([JsonDecoder] and [JsonEncoder]).
 */
@PublishedApi
internal object JsonNullSerializer : KSerializer<JsonNull> {
  // technically, JsonNull is an object, but it does not call beginStructure/endStructure at all
  override val descriptor: SerialDescriptor =
    buildSerialDescriptor("kotlinx.serialization.json.JsonNull", SerialKind.ENUM)

  override fun serialize(encoder: Encoder, value: JsonNull) {
    encoder.encodeNull()
  }

  override fun deserialize(decoder: Decoder): JsonNull {
    if (decoder.decodeNotNullMark()) {
      throw JsonDecodingException("Expected 'null' literal")
    }
    decoder.decodeNull()
    return JsonNull
  }
}

private object JsonLiteralSerializer : KSerializer<JsonLiteral> {

  override val descriptor: SerialDescriptor =
    PrimitiveSerialDescriptor("kotlinx.serialization.json.JsonLiteral", PrimitiveKind.STRING)

  @Suppress("ReturnCount")
  override fun serialize(encoder: Encoder, value: JsonLiteral) {
    if (value.isString) {
      return encoder.encodeString(value.content)
    }

    value.coerceToInlineType?.let {
      return encoder.encodeInline(it).encodeString(value.content)
    }

    // use .content instead of .longOrNull as latter can process exponential notation,
    // and it should be delegated to double when encoding.
    value.content.toLongOrNull()?.let {
      return encoder.encodeLong(it)
    }

    // most unsigned values fit to .longOrNull, but not ULong
    value.content.toULongOrNull()?.let {
      encoder.encodeInline(ULong.serializer().descriptor).encodeLong(it.toLong())
      return
    }

    value.content.toDoubleOrNull()?.let {
      return encoder.encodeDouble(it)
    }
    value.content.toBooleanStrictOrNull()?.let {
      return encoder.encodeBoolean(it)
    }

    encoder.encodeString(value.content)
  }

  override fun deserialize(decoder: Decoder): JsonLiteral {
    val result = decoder.asJsonDecoder().decodeJsonElement()
    if (result !is JsonLiteral) {
      throw JsonDecodingException(
        -1,
        "Unexpected JSON element, expected JsonLiteral, had ${result::class}",
        result.toString(),
      )
    }
    return result
  }
}

/**
 * Serializer object providing [SerializationStrategy] and [DeserializationStrategy] for
 * [JsonObject]. It can only be used by with [Json] format an its input ([JsonDecoder] and
 * [JsonEncoder]).
 */
@PublishedApi
internal object JsonObjectSerializer : KSerializer<JsonObject> {

  private object JsonObjectDescriptor :
    SerialDescriptor by MapSerializer(String.serializer(), JsonElementKamlSerializer).descriptor {
    @ExperimentalSerializationApi
    override val serialName: String = "kotlinx.serialization.json.JsonObject"
  }

  override val descriptor: SerialDescriptor = JsonObjectDescriptor

  override fun serialize(encoder: Encoder, value: JsonObject) {
    MapSerializer(String.serializer(), JsonElementKamlSerializer).serialize(encoder, value)
  }

  override fun deserialize(decoder: Decoder): JsonObject {
    return JsonObject(
      MapSerializer(String.serializer(), JsonElementKamlSerializer).deserialize(decoder)
    )
  }
}

/**
 * Serializer object providing [SerializationStrategy] and [DeserializationStrategy] for
 * [JsonArray]. It can only be used by with [Json] format an its input ([JsonDecoder] and
 * [JsonEncoder]).
 */
@PublishedApi
internal object JsonArraySerializer : KSerializer<JsonArray> {

  private object JsonArrayDescriptor :
    SerialDescriptor by ListSerializer(JsonElementKamlSerializer).descriptor {
    @ExperimentalSerializationApi
    override val serialName: String = "kotlinx.serialization.json.JsonArray"
  }

  override val descriptor: SerialDescriptor = JsonArrayDescriptor

  override fun serialize(encoder: Encoder, value: JsonArray) {
    ListSerializer(JsonElementKamlSerializer).serialize(encoder, value)
  }

  override fun deserialize(decoder: Decoder): JsonArray {
    return JsonArray(ListSerializer(JsonElementKamlSerializer).deserialize(decoder))
  }
}

internal fun Decoder.asJsonDecoder(): JsonDecoder =
  this as? JsonDecoder
    ?: throw IllegalStateException(
      "This serializer can be used only with Json format." +
        "Expected Decoder to be JsonDecoder, got ${this::class}"
    )

internal fun Encoder.asJsonEncoder() =
  this as? JsonEncoder
    ?: throw IllegalStateException(
      "This serializer can be used only with Json format." +
        "Expected Encoder to be JsonEncoder, got ${this::class}"
    )

/**
 * Returns serial descriptor that delegates all the calls to descriptor returned by [deferred]
 * block. Used to resolve cyclic dependencies between recursive serializable structures.
 */
@OptIn(ExperimentalSerializationApi::class)
private fun defer(deferred: () -> SerialDescriptor): SerialDescriptor =
  object : SerialDescriptor {

    private val original: SerialDescriptor by lazy(deferred)

    override val serialName: String
      get() = original.serialName

    override val kind: SerialKind
      get() = original.kind

    override val elementsCount: Int
      get() = original.elementsCount

    override fun getElementName(index: Int): String = original.getElementName(index)

    override fun getElementIndex(name: String): Int = original.getElementIndex(name)

    override fun getElementAnnotations(index: Int): List<Annotation> =
      original.getElementAnnotations(index)

    override fun getElementDescriptor(index: Int): SerialDescriptor =
      original.getElementDescriptor(index)

    override fun isElementOptional(index: Int): Boolean = original.isElementOptional(index)
  }
