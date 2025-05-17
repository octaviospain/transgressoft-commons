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

package net.transgressoft.commons.persistence.json

import net.transgressoft.commons.persistence.ReactivePrimitive
import net.transgressoft.commons.persistence.json.primitives.ReactiveBoolean
import net.transgressoft.commons.persistence.json.primitives.ReactiveInt
import net.transgressoft.commons.persistence.json.primitives.ReactiveString
import java.io.File
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * A specialized JSON repository for storing primitive values (strings, integers, booleans) in a reactive way.
 *
 * This repository provides a convenient way to store configuration values or settings
 * that need to be persisted and may change during application execution. It uses reactive
 * primitives to enable subscription to value changes.
 *
 * The class handles serialization of primitive values to a simple flat JSON structure,
 * where keys are the IDs and values are the primitive values themselves. Changes to the values
 * are automatically persisted with debouncing to prevent excessive file I/O operations when
 * multiple values change in rapid succession.
 *
 * Example usage:
 * ```
 * val config = FlexibleJsonFileRepository(File("config.json"))
 *
 * // Create reactive primitives
 * val serverName = config.createReactiveString("server.name", "Main Server")
 * val port = config.createReactiveInt("server.port", 8080)
 * val debugMode = config.createReactiveBoolean("debug.mode", false)
 *
 * // Later, change a value (automatically persisted)
 * port.value = 9090
 * ```
 *
 * @param jsonFile The JSON file to store primitive values in
 * @param ioScope The coroutine scope used for file I/O operations. By default, uses a scope with
 *        limitedParallelism(1) on the IO dispatcher to ensure thread-safe file access. For testing,
 *        provide a scope created with a test dispatcher to control virtual time execution.
 */
@Suppress("UNCHECKED_CAST")
open class FlexibleJsonFileRepository(jsonFile: File) :
    JsonFileRepository<String, ReactivePrimitive<Any>>(
        jsonFile,
        ReactiveValueMapSerializer
    ) {
    fun createReactiveString(id: String, value: String) = ReactiveString(id, value).also { add(it as ReactivePrimitive<Any>) }

    fun createReactiveBoolean(id: String, value: Boolean) = ReactiveBoolean(id, value).also { add(it as ReactivePrimitive<Any>) }

    fun createReactiveInt(id: String, value: Int) = ReactiveInt(id, value).also { add(it as ReactivePrimitive<Any>) }
}

object ReactiveValueMapSerializer : KSerializer<Map<String, ReactivePrimitive<Any>>> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Map<String, ReactivePrimitive<Any>>")

    override fun serialize(encoder: Encoder, value: Map<String, ReactivePrimitive<Any>>) {
        val jsonEncoder = encoder as? JsonEncoder ?: throw SerializationException("This serializer can be used only with Json format")

        val jsonObject =
            buildJsonObject {
                value.forEach { (key, reactiveValue) ->
                    when (val primitiveValue = reactiveValue.value) {
                        is String -> put(key, primitiveValue)
                        is Boolean -> put(key, primitiveValue)
                        is Int -> put(key, primitiveValue)
                        else -> throw SerializationException("Unsupported ReactiveValue type: ${reactiveValue::class}")
                    }
                }
            }

        jsonEncoder.encodeJsonElement(jsonObject)
    }

    @Suppress("UNCHECKED_CAST")
    override fun deserialize(decoder: Decoder): Map<String, ReactivePrimitive<Any>> {
        val jsonDecoder = decoder as? JsonDecoder ?: throw SerializationException("This serializer can be used only with Json format")
        val jsonObject = jsonDecoder.decodeJsonElement().jsonObject
        return jsonObject.mapValues { (key, element) ->
            when {
                element is JsonPrimitive && element.isString -> ReactiveString(key, element.content) as ReactivePrimitive<Any>
                element is JsonPrimitive && element.booleanOrNull != null -> ReactiveBoolean(key, element.boolean) as ReactivePrimitive<Any>
                element is JsonPrimitive && element.intOrNull != null -> ReactiveInt(key, element.int) as ReactivePrimitive<Any>
                else -> throw SerializationException("Unsupported JSON element type for key: $key")
            }
        }
    }
}