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

import net.transgressoft.commons.entity.ReactiveEntity
import net.transgressoft.commons.event.CrudEvent
import net.transgressoft.commons.event.EntityChangeEvent
import net.transgressoft.commons.event.ReactiveScope
import net.transgressoft.commons.event.TransEventSubscription
import net.transgressoft.commons.persistence.RepositoryBase
import mu.KotlinLogging
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
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
 * - Asynchronous JSON serialization using debouncing to optimize I/O operations
 * - Automatic persistence of all repository operations
 * - Thread-safe operations using ConcurrentHashMap by the upstream [RepositoryBase]
 * - Error handling with logging
 * - Subscription management for entity lifecycle
 *
 * @param name A descriptive name for this repository, used in logging
 * @param K The type of entity identifier, must be [Comparable]
 * @param R The type of entity being stored, must implement [ReactiveEntity]
 * @param file The JSON file to store entities in
 * @param mapSerializer The serializer used to convert entities to/from JSON
 * @param repositorySerializersModule Optional module for configuring JSON serialization
 */
abstract class JsonFileRepositoryBase<K : Comparable<K>, R : ReactiveEntity<K, R>>(
    name: String,
    file: File,
    private val mapSerializer: KSerializer<Map<K, R>>,
    private val repositorySerializersModule: SerializersModule = SerializersModule {}
) : RepositoryBase<K, R>("$name-$file", ConcurrentHashMap()), JsonRepository<K, R> {
    private val log = KotlinLogging.logger(javaClass.name)

    final override var jsonFile: File = file
        set(value) {
            require(value.exists().and(value.canWrite()).and(value.extension == "json").and(value.readText().isEmpty())) {
                "Provided jsonFile does not exist, is not writable, is not a json file, or is not empty"
            }
            field = value
            serializationEventChannel.trySend(Unit)
            log.info { "jsonFile set to $value" }
        }

    protected val json =
        Json {
            serializersModule = repositorySerializersModule
            prettyPrint = true
            explicitNulls = true
            allowStructuredMapKeys = true
        }

    /**
     * The coroutine scope used for file I/O operations. Defaults to a scope with
     * limitedParallelism(1) on the IO dispatcher to ensure sequential file access and thread safety.
     * For testing, provide a scope with a test dispatcher.
     * @see [ReactiveScope]
     */
    private val ioScope: CoroutineScope = ReactiveScope.ioScope

    /**
     * This coroutine scope is used to handle all emissions to the
     * json serialization job for a fire and forget approach
     */
    private val flowScope: CoroutineScope = ReactiveScope.flowScope

    private val serializationEventChannel = Channel<Unit>(Channel.CONFLATED)

    /**
     * Subscriptions map for each entity in the repository is needed in order to unsubscribe
     * from their changes once they are removed.
     */
    private val subscriptionsMap: MutableMap<K, TransEventSubscription<in R, CrudEvent.Type, EntityChangeEvent<K, R>>> = ConcurrentHashMap()

    init {
        require(jsonFile.exists().and(jsonFile.canWrite()).and(jsonFile.extension == "json")) {
            "Provided jsonFile does not exist, is not writable or is not a json file"
        }

        flowScope.launch {
            for (event in serializationEventChannel) {
                try {
                    serializationTrigger.emit(event)
                } catch (exception: Exception) {
                    log.error(exception) { "Unexpected error during serialization" }
                }
            }
        }

        // Load entities from JSON file on initialization and create subscriptions
        decodeFromJson()?.let { loadedEntities ->
            log.info { "${loadedEntities.size} objects deserialized from file $jsonFile" }

            entitiesById.putAll(loadedEntities)

            // Create subscriptions for loaded entities
            flowScope.launch {
                entitiesById.values.forEach { entity ->
                    val subscription = entity.subscribe { serializationEventChannel.trySend(Unit) }
                    subscriptionsMap[entity.id] = subscription
                }
            }
        }
    }

    /**
     * Shared flow used to trigger serialization of the repository state. Debounced to avoid
     * excessive serialization operations when multiple changes occur in a short period.
     */
    private val serializationTrigger = MutableSharedFlow<Unit>(replay = 1, extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private val serializationDelay = 300.milliseconds

    @OptIn(FlowPreview::class)
    private val serializationJob =
        ioScope.launch {
            serializationTrigger
                .debounce(serializationDelay)
                .collect {
                    performSerialization()
                }
        }

    private suspend fun performSerialization() {
        try {
            val jsonString = json.encodeToString(mapSerializer, entitiesById)

            // Limit serialization to one concurrent operation
            withContext(ioScope.coroutineContext) {
                jsonFile.writeText(jsonString)
            }
            log.debug { "File updated: $jsonFile" }
        } catch (exception: Exception) {
            log.error(exception) { "Error serializing to file $jsonFile" }
        }
    }

    private fun decodeFromJson(): Map<K, R>? =
        if (jsonFile.readText().isNotEmpty()) {
            json.decodeFromString(mapSerializer, jsonFile.readText())
        } else null

    override fun close() {
        runBlocking {
            // Ensure any pending serialization is performed
            performSerialization()
        }
        serializationJob.cancel()
    }

    override fun add(entity: R) =
        super.add(entity).also { added ->
            if (added) {
                serializationEventChannel.trySend(Unit)
                val subscription = entity.subscribe { serializationEventChannel.trySend(Unit) }
                subscriptionsMap[entity.id] = subscription
            }
        }

    override fun addOrReplace(entity: R) =
        super.addOrReplace(entity).also { added ->
            if (added) {
                serializationEventChannel.trySend(Unit)
                val subscription = entity.subscribe { serializationEventChannel.trySend(Unit) }
                subscriptionsMap[entity.id] = subscription
            }
        }

    override fun addOrReplaceAll(entities: Set<R>) =
        super.addOrReplaceAll(entities).also { added ->
            if (added) {
                serializationEventChannel.trySend(Unit)
                entities.forEach { entity ->
                    val subscription = entity.subscribe { serializationEventChannel.trySend(Unit) }
                    subscriptionsMap[entity.id] = subscription
                }
            }
        }

    override fun remove(entity: R) =
        super.remove(entity).also { removed ->
            if (removed) {
                serializationEventChannel.trySend(Unit)
                subscriptionsMap[entity.id]?.cancel() ?: error("Repository should contain a subscription for $entity")
                subscriptionsMap.remove(entity.id)
            }
        }

    override fun removeAll(entities: Collection<R>) =
        super.removeAll(entities).also { removed ->
            if (removed) {
                serializationEventChannel.trySend(Unit)
                entities.forEach {
                    subscriptionsMap[it.id]?.cancel() ?: error("Repository should contain a subscription for $it")
                    subscriptionsMap.remove(it.id)
                }
            }
        }

    override fun clear() {
        super.clear()
        serializationEventChannel.trySend(Unit)
        subscriptionsMap.forEach { (_, subscription) ->
            subscription.cancel()
        }
        subscriptionsMap.clear()
    }
}