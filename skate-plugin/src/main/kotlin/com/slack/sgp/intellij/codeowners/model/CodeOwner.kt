/*
 * Copyright (C) 2024 Slack Technologies, LLC
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
package com.slack.sgp.intellij.codeowners.model

import com.charleskorn.kaml.YamlInput
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlScalar
import kotlinx.serialization.*
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

object StringOrObjectSerializer : KSerializer<Path> {
    private val stringDescriptor = String.serializer().descriptor
    private val objectDescriptor = buildClassSerialDescriptor("Path") {
        element<String>("path")
        element<Boolean>("notify", isOptional = true)
    }

    @OptIn(ExperimentalSerializationApi::class, InternalSerializationApi::class)
    override val descriptor: SerialDescriptor = buildSerialDescriptor(
        StringOrObjectSerializer::class.java.name,
        SerialKind.CONTEXTUAL
    ) {
        element("object", objectDescriptor)
        element("string", stringDescriptor)
    }

    override fun deserialize(decoder: Decoder): Path {
        val tree = decoder.beginStructure(descriptor) as YamlInput
        val result = when (tree.node) {
            is YamlScalar -> beginAndDecodeString(tree)
            is YamlMap -> beginAndDecodeObject(tree)
            else -> error("Unexpected type, path should be string or map")
        }
        return result
    }

    private fun beginAndDecodeString(decoder: YamlInput): Path {
        val input = decoder.beginStructure(stringDescriptor) as YamlInput
        val path = input.decodeString().also { decoder.endStructure(descriptor) }
        return Path(path, true)
    }

    private fun beginAndDecodeObject(decoder: YamlInput): Path {
        var path = ""
        var notify = true
        decoder.decodeStructure(objectDescriptor) {
            while (true) {
                when (val index = decodeElementIndex(objectDescriptor)) {
                    0 -> path = decodeStringElement(objectDescriptor, 0)
                    1 -> notify = decodeBooleanElement(objectDescriptor, 1)
                    CompositeDecoder.DECODE_DONE -> break
                    else -> error("Unexpected index: $index")
                }
            }
        }
        return Path(path, notify)
    }

    override fun serialize(encoder: Encoder, value: Path) {
        TODO("Not yet implemented")
    }

}

@Serializable
data class Path(val path: String, val notify: Boolean?)

@Serializable
data class CodeOwner(val name: String, val paths: List<@Serializable(with = StringOrObjectSerializer::class) Path>)
