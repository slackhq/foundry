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

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

object PathDeserializer : KSerializer<Path> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Path") {
        element("path", String.serializer().descriptor)
        element("notify", Boolean.serializer().descriptor)
    }

    @OptIn(ExperimentalSerializationApi::class)
    override fun deserialize(decoder: Decoder): Path {
        return decoder.decodeStructure(descriptor) {
            var path = ""
            var notify = true
            while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    0 -> path = decodeStringElement(descriptor, 0)
                    1 -> notify = decodeBooleanElement(descriptor, 1)
                    CompositeDecoder.DECODE_DONE -> break
                    else -> error("Unexpected index: $index")
                }
            }
            Path(path, notify)
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    override fun serialize(encoder: Encoder, value: Path) {
        encoder.encodeStructure(descriptor) {
            encodeStringElement(descriptor, 0, value.path)
        }
    }
}

@Serializable(with = PathDeserializer::class)
data class Path(val path: String, val notify: Boolean?)

@Serializable
data class CodeOwner(val name: String, val paths: List<@Serializable(with = PathDeserializer::class)Path>)
