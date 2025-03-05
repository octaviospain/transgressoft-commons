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

package net.transgressoft.commons.data

import net.transgressoft.commons.IdentifiableEntity
import mu.KotlinLogging
import java.util.*
import java.util.function.Consumer
import java.util.function.Predicate
import java.util.stream.Collectors

/**
 * Base class for read-only entity registries with reactive query capabilities.
 *
 * `RegistryBase` provides the foundation for entity collections that can be searched and
 * queried, with changes tracked and published to subscribers. It follows a reactive approach
 * by implementing [java.util.concurrent.Flow.Publisher], notifying subscribers of read operations
 * and entity changes.
 *
 * Key features:
 * - Run actions on entities that automatically detect and publish changes
 * - Rich query capabilities with predicate-based searches
 * - Event publishing for entity reads and modifications
 * - Thread-safe operation
 *
 * @param K The type of entity identifier, must be [Comparable]
 * @param T The type of entity being stored, must implement [IdentifiableEntity]
 * @property name A descriptive name for this registry, used in logging
 * @property entitiesById The internal map storing entities by their IDs
 *
 * @see [CrudEventPublisherBase]
 */
abstract class RegistryBase<K, T : IdentifiableEntity<K>>(
    name: String,
    protected val entitiesById: MutableMap<K, T> = hashMapOf()
) : CrudEventPublisherBase<K, T>(name),
    Registry<K, T> where K : Comparable<K> {
    private val log = KotlinLogging.logger(javaClass.name)

    @Suppress("UNCHECKED_CAST")
    override fun runForSingle(id: K, entityAction: Consumer<T>): Boolean =
        Optional.ofNullable(entitiesById[id]).map {
            val previousHashcode = it.hashCode()
            val entityBeforeUpdate = it.clone() as T
            entityAction.accept(it)
            if (previousHashcode != it.hashCode()) {
                log.debug { "Entity with id ${it.id} was modified as a result of an action" }
                putUpdateEvent(it, entityBeforeUpdate)
                true
            } else false
        }.orElse(false)

    override fun runForMany(ids: Set<K>, entityAction: Consumer<T>): Boolean =
        ids.mapNotNull { entitiesById[it] }.let {
            if (it.isNotEmpty()) {
                runActionAndReplaceModifiedEntities(it.toSet(), entityAction)
            } else {
                false
            }
        }

    @Suppress("UNCHECKED_CAST")
    private fun runActionAndReplaceModifiedEntities(entities: Set<T>, entityAction: Consumer<T>): Boolean {
        val updatedEntities = mutableListOf<T>()
        val entitiesBeforeUpdate = mutableListOf<T>()

        entities.forEach {
            val previousHashCode = it.hashCode()
            val entityBeforeChange = it.clone() as T
            entityAction.accept(it)
            if (previousHashCode != it.hashCode()) {
                log.debug { "Entity with id ${it.id} was modified as a result of an action" }
                entitiesBeforeUpdate.add(entityBeforeChange)
                updatedEntities.add(it)
            }
        }

        if (updatedEntities.isNotEmpty()) {
            putUpdateEvent(updatedEntities, entitiesBeforeUpdate)
            return true
        }

        return false
    }

    override fun runMatching(predicate: Predicate<T>, entityAction: Consumer<T>): Boolean =
        search(predicate).let {
            if (it.isNotEmpty()) {
                runActionAndReplaceModifiedEntities(it, entityAction)
            } else {
                false
            }
        }

    override fun runForAll(entityAction: Consumer<T>) = runActionAndReplaceModifiedEntities(entitiesById.values.toSet(), entityAction)

    override fun contains(id: K) = entitiesById.containsKey(id)

    override fun contains(predicate: Predicate<T>): Boolean =
        entitiesById.values.stream()
            .filter { predicate.test(it) }
            .findAny().isPresent

    override fun search(predicate: Predicate<T>): Set<T> =
        entitiesById.values.stream()
            .filter { predicate.test(it) }
            .collect(Collectors.toSet())
            .also { putReadEvent(it) }

    override fun findFirst(predicate: Predicate<T>): Optional<T> =
        Optional.ofNullable(entitiesById.values.firstOrNull { predicate.test(it) })
            .also {
                if (it.isPresent)
                    putReadEvent(it.get())
            }

    override fun findById(id: K): Optional<T> =
        Optional.ofNullable(entitiesById[id])
            .also {
                if (it.isPresent)
                    putReadEvent(it.get())
            }

    override fun findByUniqueId(uniqueId: String): Optional<T> =
        entitiesById.values.stream()
            .filter { it.uniqueId == uniqueId }
            .findAny()
            .also {
                if (it.isPresent)
                    putReadEvent(it.get())
            }

    override fun size() = entitiesById.size

    override val isEmpty: Boolean
        get() = entitiesById.isEmpty()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as RegistryBase<*, *>
        return entitiesById == that.entitiesById
    }

    override fun hashCode() = Objects.hash(entitiesById)
}