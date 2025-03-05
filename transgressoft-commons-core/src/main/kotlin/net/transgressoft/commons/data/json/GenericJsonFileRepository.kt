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

import net.transgressoft.commons.EntityChangeEvent
import net.transgressoft.commons.EntityChangeSubscriber
import net.transgressoft.commons.ReactiveEntity
import net.transgressoft.commons.ReactiveEntitySubscription
import net.transgressoft.commons.data.StandardCrudEvent.Type.UPDATE
import mu.KotlinLogging
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.KSerializer
import kotlinx.serialization.modules.SerializersModule

/**
 * An implementation of [JsonFileRepositoryBase] that automatically tracks changes to reactive entities.
 *
 * This class extends the basic JSON file persistence with reactive entity change tracking.
 * When an entity stored in this repository changes state, those changes are automatically
 * detected and persisted to the JSON file without requiring explicit calls to update methods.
 *
 * Key features:
 * - Automatic subscription to stored reactive entities
 * - Subscription management for entity lifecycle
 * - Persistent change tracking for all repository entities
 *
 * @param K The type of entity identifier, must be [Comparable]
 * @param R The type of entity being stored, must implement [ReactiveEntity]
 * @param file The JSON file to store entities in
 * @param mapSerializer The serializer used to convert entities to/from JSON
 * @param repositorySerializersModule Optional module for configuring JSON serialization
 * @param name A descriptive name for this repository, used in logging
 */
open class GenericJsonFileRepository<K : Comparable<K>, R : ReactiveEntity<K, R>>
    @JvmOverloads
    constructor(
        file: File,
        mapSerializer: KSerializer<Map<K, R>>,
        repositorySerializersModule: SerializersModule = SerializersModule {},
        name: String
    ) : JsonFileRepositoryBase<K, R>(file, mapSerializer, repositorySerializersModule, name) {
        private val log = KotlinLogging.logger(javaClass.name)

        private val subscriptionsMap: MutableMap<K, ReactiveEntitySubscription<in EntityChangeEvent<K, R>, R, K>> = ConcurrentHashMap()

        private val reactiveEntityUpdateSubscriber =
            EntityChangeSubscriber<R, EntityChangeEvent<K, R>, K>("entityUpdaterSubscriber").apply {
                addOnNextEventAction(UPDATE) {
                    serializeToJson()
                }
                addOnSubscribeEventAction {
                    @Suppress("UNCHECKED_CAST")
                    val reactiveSubscription = it as ReactiveEntitySubscription<EntityChangeEvent<K, R>, R, K>
                    subscriptionsMap[reactiveSubscription.source.id] = it
                }
            }

        override fun add(entity: R) =
            super.add(entity).also { added ->
                if (added) {
                    entity.subscribe(reactiveEntityUpdateSubscriber)
                }
            }

        override fun addOrReplace(entity: R) =
            super.addOrReplace(entity).also { added ->
                if (added) {
                    entity.subscribe(reactiveEntityUpdateSubscriber)
                }
            }

        override fun addOrReplaceAll(entities: Set<R>) =
            super.addOrReplaceAll(entities).also { added ->
                if (added) {
                    entities.forEach { it.subscribe(reactiveEntityUpdateSubscriber) }
                }
            }

        override fun remove(entity: R) =
            super.remove(entity).also { removed ->
                if (removed) {
                    subscriptionsMap[entity.id]?.cancel()
                }
            }

        override fun removeAll(entities: Set<R>) =
            super.removeAll(entities).also { removed ->
                if (removed) {
                    entities.forEach { subscriptionsMap[it.id]?.cancel() }
                }
            }
    }