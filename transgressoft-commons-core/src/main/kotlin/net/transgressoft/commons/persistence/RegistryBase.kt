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

package net.transgressoft.commons.persistence

import net.transgressoft.commons.entity.IdentifiableEntity
import net.transgressoft.commons.event.CrudEvent
import net.transgressoft.commons.event.CrudEvent.Type.UPDATE
import net.transgressoft.commons.event.FlowEventPublisher
import net.transgressoft.commons.event.StandardCrudEvent.Read
import net.transgressoft.commons.event.StandardCrudEvent.Update
import net.transgressoft.commons.event.TransEventPublisher
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
 * by implementing [TransEventPublisher], notifying subscribers of read operations
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
 * @property entitiesById The internal map storing entities by their IDs
 *
 * @see [net.transgressoft.commons.event.TransEventSubscriber]
 */
abstract class RegistryBase<K, T : IdentifiableEntity<K>>(
    protected val entitiesById: MutableMap<K, T> = hashMapOf(),
    protected val publisher: TransEventPublisher<CrudEvent.Type, CrudEvent<K, T>> = FlowEventPublisher("Registry")
) : TransEventPublisher<CrudEvent.Type, CrudEvent<K, T>> by publisher,
    Registry<K, T> where K : Comparable<K> {
    private val log = KotlinLogging.logger(javaClass.name)

    init {
        // A registry can't create or delete entities,
        // so the CREATE and DELETE events are disabled by default
        // READ is disabled also because its use case is not clear yet
        activateEvents(UPDATE)
    }

    @Suppress("UNCHECKED_CAST")
    override fun runForSingle(id: K, entityAction: Consumer<T>): Boolean {
        val entity = entitiesById[id] ?: return false
        val previousHashcode = entity.hashCode()
        val entityBeforeUpdate = entity.clone() as T

        entityAction.accept(entity)

        if (previousHashcode != entity.hashCode()) {
            log.debug { "Entity with id ${entity.id} was modified as a result of an action" }
            publisher.emitAsync(Update(entity, entityBeforeUpdate))
            return true
        }
        return false
    }

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
        val updates =
            entities.mapNotNull { entity ->
                val previousHashCode = entity.hashCode()
                val entityBeforeChange = entity.clone() as T

                entityAction.accept(entity)

                if (previousHashCode != entity.hashCode()) {
                    // Ensure the entity in the map is still this entity
                    entitiesById.computeIfPresent(entity.id) { _, current ->
                        if (current == entityBeforeChange)
                            entity
                        else current
                    }
                    log.debug { "Entity with id ${entity.id} was modified as a result of an action" }
                    Pair(entity, entityBeforeChange)
                } else null
            }

        if (updates.isNotEmpty()) {
            val (modified, originals) = updates.unzip()
            publisher.emitAsync(Update(modified, originals))
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
            .also { publisher.emitAsync(Read(it)) }

    override fun search(size: Int, predicate: Predicate<T>): Set<T> =
        entitiesById.values.asSequence()
            .filter { predicate.test(it) }
            .take(size)
            .toSet()
            .also { publisher.emitAsync(Read(it)) }

    override fun findFirst(predicate: Predicate<T>): Optional<T> =
        Optional.ofNullable(entitiesById.values.firstOrNull { predicate.test(it) })
            .also {
                if (it.isPresent)
                    publisher.emitAsync(Read(it.get()))
            }

    override fun findById(id: K): Optional<T> =
        Optional.ofNullable(entitiesById[id])
            .also {
                if (it.isPresent)
                    publisher.emitAsync(Read(it.get()))
            }

    override fun findByUniqueId(uniqueId: String): Optional<T> =
        entitiesById.values.stream()
            .filter { it.uniqueId == uniqueId }
            .findAny()
            .also {
                if (it.isPresent)
                    publisher.emitAsync(Read(it.get()))
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