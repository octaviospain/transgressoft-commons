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
import java.util.Objects
import java.util.stream.Collectors.partitioningBy

/**
 * Base class for mutable entity repositories with reactive behavior.
 *
 * `RepositoryBase` extends [RegistryBase] to provide full CRUD (Create, Read, Update, Delete)
 * operations on entities. It maintains a collection of entities that can be modified through
 * add, remove, and replace operations, with all changes automatically published to subscribers.
 *
 * Key features:
 * - Full CRUD operations with event publishing
 * - Bulk operations for adding/replacing/removing multiple entities
 * - Optimized entity replacement with change detection
 * - Detailed logging of repository operations
 *
 * This class serves as the foundation for concrete repository implementations like
 * [VolatileRepository] and [JsonFileRepository].
 *
 * @param K The type of entity identifier, must be [Comparable]
 * @param T The type of entity being stored, must implement [IdentifiableEntity]
 * @property name A descriptive name for this repository, used in logging
 * @property initialEntities Optional map of entities to initialize the repository with
 */
abstract class RepositoryBase<K : Comparable<K>, T : IdentifiableEntity<K>>(
    name: String,
    initialEntities: MutableMap<K, T> = hashMapOf()
) : RegistryBase<K, T>(name, initialEntities), Repository<K, T> {
    private val log = KotlinLogging.logger(javaClass.name)

    override fun add(entity: T): Boolean {
        if (!contains(entity.id)) {
            entitiesById[entity.id] = entity
            putCreateEvent(entity)
            log.debug { "Entity with id ${entity.id} added to repository: $entity" }
            return true
        }

        return false
    }

    override fun addOrReplace(entity: T): Boolean =
        add(entity).let { added ->
            if (!added) {
                replace(entity)
            } else {
                true
            }
        }

    private fun replace(entity: T): Boolean {
        var wasReplaced = false
        entitiesById.computeIfPresent(entity.id) { _, storedEntity ->
            if (storedEntity != entity) {
                wasReplaced = true
                log.debug { "Entity with id ${entity.id} was replaced by $entity" }
                putUpdateEvent(entity, storedEntity)
                entity
            } else
                storedEntity
        }

        return wasReplaced
    }

    override fun addOrReplaceAll(entities: Set<T>): Boolean {
        val entitiesBeforeUpdate = mutableListOf<T>()

        val addedAndReplaced =
            entities.stream().filter { it != null && entitiesById.containsValue(it).not() }
                .collect(
                    partitioningBy { entity ->
                        val entityBefore = entitiesById[entity.id]
                        if (entityBefore != null)
                            entitiesBeforeUpdate.add(entityBefore)
                        entitiesById[entity.id] = entity
                        return@partitioningBy entityBefore == null
                    }
                )

        addedAndReplaced[true]?.let {
            if (it.isNotEmpty()) {
                putCreateEvent(it)
                log.debug { "${it.size} entities were added: $it" }
            }
        }
        addedAndReplaced[false]?.let {
            if (it.isNotEmpty()) {
                putUpdateEvent(it, entitiesBeforeUpdate)
                log.debug { "${it.size} entities were replaced: $it" }
            }
        }

        return addedAndReplaced.isNotEmpty()
    }

    override fun remove(entity: T): Boolean =
        entitiesById.remove(entity.id).let { removed ->
            if (removed != null) {
                putDeleteEvent(entity)
                log.debug { "Entity with id ${entity.id} was removed: $entity" }
            }
            removed != null
        }

    override fun removeAll(entities: Set<T>): Boolean {
        val removedOrNot = entities.stream().collect(partitioningBy { entitiesById.remove(it.id, it) })
        removedOrNot[true]?.let {
            if (it.isNotEmpty()) {
                putDeleteEvent(it)
                log.debug { "${it.size} entities were removed: $it" }
            }
        }
        return removedOrNot[true]?.isNotEmpty() ?: false
    }

    override fun clear() {
        if (entitiesById.isNotEmpty()) {
            putDeleteEvent(entitiesById.values.toSet())
            log.debug { "${entitiesById.size} entities were removed resulting in empty repository" }
            entitiesById.clear()
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as RepositoryBase<*, *>
        return entitiesById == that.entitiesById
    }

    override fun hashCode() = Objects.hash(entitiesById)
}