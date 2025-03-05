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

package net.transgressoft.commons.data.json

import net.transgressoft.commons.ReactiveEntity
import net.transgressoft.commons.data.RepositoryBase
import mu.KotlinLogging
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule

/**
 * Base abstract class for repositories that store entities in a JSON file.
 *
 * This class handles the serialization and deserialization of entities to/from a JSON file,
 * providing persistent storage with asynchronous write operations. It extends [RepositoryBase]
 * with file I/O capabilities, ensuring that repository operations are automatically persisted.
 *
 * Key features:
 * - Asynchronous JSON serialization using a dedicated thread pool
 * - Automatic persistence of all repository operations
 * - Thread-safe operations using ConcurrentHashMap
 * - Error handling with logging
 *
 * @param K The type of entity identifier, must be [Comparable]
 * @param R The type of entity being stored, must implement [ReactiveEntity]
 * @param file The JSON file to store entities in
 * @param mapSerializer The serializer used to convert entities to/from JSON
 * @param repositorySerializersModule Optional module for configuring JSON serialization
 * @param name A descriptive name for this repository, used in logging
 */
abstract class JsonFileRepositoryBase<K : Comparable<K>, R : ReactiveEntity<K, R>>(
    file: File,
    private val mapSerializer: KSerializer<Map<K, R>>,
    private val repositorySerializersModule: SerializersModule = SerializersModule {},
    name: String
) : RepositoryBase<K, R>("$name-$file", ConcurrentHashMap()), JsonRepository<K, R> {
    private val log = KotlinLogging.logger(javaClass.name)

    final override var jsonFile: File = file
        set(value) {
            require(value.exists().and(value.canWrite()).and(value.extension == "json").and(value.readText().isEmpty())) {
                "Provided jsonFile does not exist, is not writable, is not a json file, or is not empty"
            }
            field = value
            serializeToJson()
            log.info { "jsonFile set to $value" }
        }

    protected val json =
        Json {
            serializersModule = repositorySerializersModule
            prettyPrint = true
            explicitNulls = true
            allowStructuredMapKeys = true
        }

    init {
        require(this.jsonFile.exists().and(this.jsonFile.canWrite()).and(this.jsonFile.extension == "json")) {
            "Provided jsonFile does not exist, is not writable or is not a json file"
        }
        decodeFromJson()?.let { entitiesById.putAll(it) }
    }

    private val executorService =
        Executors.newFixedThreadPool(1) { runnable ->
            Thread(runnable).apply {
                isDaemon = true
                setUncaughtExceptionHandler { thread, exception ->
                    log.error(exception) { "Error in thread $thread" }
                }
            }
        }

    protected fun serializeToJson() {
        jsonFile.run {
            executorService.execute {
                json.encodeToString(mapSerializer, entitiesById).also {
                    jsonFile.writeText(it)
                    log.debug { "${this@JsonFileRepositoryBase.name} serialized to file $jsonFile" }
                }
            }
        }
    }

    private fun decodeFromJson(): Map<K, R>? =
        if (jsonFile.readText().isNotEmpty()) {
            json.decodeFromString(mapSerializer, jsonFile.readText())
        } else null

    override fun dispose() {
        executorService.shutdown()
    }

    override fun add(entity: R) =
        super.add(entity).also { added ->
            if (added) {
                serializeToJson()
            }
        }

    override fun addOrReplace(entity: R) =
        super.addOrReplace(entity).also { added ->
            if (added) {
                serializeToJson()
            }
        }

    override fun addOrReplaceAll(entities: Set<R>) =
        super.addOrReplaceAll(entities).also { added ->
            if (added) {
                serializeToJson()
            }
        }

    override fun remove(entity: R) =
        super.remove(entity).also { removed ->
            if (removed) {
                serializeToJson()
            }
        }

    override fun removeAll(entities: Set<R>) =
        super.removeAll(entities).also { removed ->
            if (removed) {
                serializeToJson()
            }
        }

    override fun clear() {
        super.clear()
        serializeToJson()
    }
}