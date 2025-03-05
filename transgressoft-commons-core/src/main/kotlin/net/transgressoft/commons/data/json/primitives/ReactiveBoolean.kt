/******************************************************************************
 *     Copyright (C) 2025  Octavio Calleya Garcia                             *
 *                                                                            *
 *     This program is free software: you can redistribute it and/or modify   *
 *     it under the terms of the GNU General Public License as published by   *
 *     the Free Software Foundation, either version 3 of the License, or      *
 *     (at your option) any later version.                                    *
 *                                                                            *
 *     This program is distributed in the hope that it will be useful,        *
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of         *
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the          *
 *     GNU General Public License for more details.                           *
 *                                                                            *
 *     You should have received a copy of the GNU General Public License      *
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>. *
 ******************************************************************************/

package net.transgressoft.commons.data.json.primitives

import net.transgressoft.commons.data.ReactivePrimitiveWrapper
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * A reactive wrapper for Boolean values.
 *
 * This class enables Boolean values to participate in the reactive entity system,
 * with changes to the value triggering notification events to subscribers. It includes
 * custom serialization for JSON persistence.
 *
 * @property id The unique identifier for this reactive boolean
 * @param initialValue The initial boolean value, can be null
 */
@Serializable(with = ReactiveBooleanSerializer::class)
class ReactiveBoolean internal constructor(id: String, initialValue: Boolean? = null) :
    ReactivePrimitiveWrapper<ReactiveBoolean, Boolean>(id, initialValue) {
        override fun clone(): ReactiveBoolean = ReactiveBoolean(id, value)

        override fun hashCode() = (id.hashCode() + value.hashCode()) * 31

        override fun equals(other: Any?) =
            when {
                this === other -> true
                other !is ReactiveBoolean -> false
                else -> id == other.id && value == other.value
            }

        override fun toString() =
            buildString {
                append("ReactiveBoolean(")
                append("id=$id, ")
                append("value=$value")
                append(")")
            }
    }

internal class ReactiveBooleanSerializer : KSerializer<ReactiveBoolean> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("ReactiveBoolean") {
            element<String>("id")
            element<Boolean>("value")
        }

    override fun serialize(encoder: Encoder, value: ReactiveBoolean) {
        val jsonEncoder = encoder as? JsonEncoder ?: throw SerializationException("This serializer can be used only with Json format")
        val jsonObject =
            buildJsonObject {
                put(value.id, value.value)
            }
        jsonEncoder.encodeJsonElement(jsonObject)
    }

    override fun deserialize(decoder: Decoder): ReactiveBoolean {
        val compositeDecoder = decoder.beginStructure(descriptor)
        val id = compositeDecoder.decodeStringElement(descriptor, 0)
        val value = compositeDecoder.decodeBooleanElement(descriptor, 1)
        compositeDecoder.endStructure(descriptor)
        return ReactiveBoolean(id, value)
    }
}