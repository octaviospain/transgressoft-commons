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

package net.transgressoft.commons.event

import net.transgressoft.commons.entity.IdentifiableEntity

/**
 * Container class that provides standard implementations of [CrudEvent] for
 * different CRUD operation types.
 *
 * This sealed class hierarchically organizes the different types of CRUD operations
 * and provides factory methods to create appropriately typed event instances.
 */
sealed class StandardCrudEvent {

    data class Create<K, T: IdentifiableEntity<K>>(override val entities: Map<K, T>): CrudEvent<K, T> where K: Comparable<K> {
        constructor(entity: T): this(mapOf(entity.id to entity))
        constructor(entities: Collection<T>): this(entities.associateBy { it.id })

        override val type: CrudEvent.Type = CrudEvent.Type.CREATE
    }

    data class Read<K, T: IdentifiableEntity<K>>(override val entities: Map<K, T>): CrudEvent<K, T> where K: Comparable<K> {
        constructor(entity: T): this(mapOf(entity.id to entity))
        constructor(entities: Collection<T>): this(entities.associateBy { it.id })

        override val type: CrudEvent.Type = CrudEvent.Type.READ
    }

    data class Update<K, T: IdentifiableEntity<K>>(override val entities: Map<K, T>, override val oldEntities: Map<K, T>):
        EntityChangeEvent<K, T> where K: Comparable<K> {

        constructor(entity: T, oldEntity: T): this(mapOf(entity.id to entity), mapOf(oldEntity.id to oldEntity))

        constructor(entities: Collection<T>, oldEntities: Collection<T>): this(
            entities.associateBy { it.id },
            oldEntities.associateBy { it.id }
        )

        init {
            require(eventCollectionsAreConsistent(entities, oldEntities)) {
                "The collections of entities and old entities must be consistent for an UPDATE event. " +
                    "They don't have the same size or they don't have the same keys."
            }
        }

        private fun eventCollectionsAreConsistent(entities: Map<K, T>, oldEntities: Map<K, T>): Boolean =
            entities.keys.containsAll(oldEntities.keys) && oldEntities.keys.containsAll(entities.keys) && entities.size == oldEntities.size

        override val type: CrudEvent.Type = CrudEvent.Type.UPDATE
    }

    data class Delete<K, T: IdentifiableEntity<K>>(override val entities: Map<K, T>): CrudEvent<K, T> where K: Comparable<K> {
        constructor(entity: T): this(mapOf(entity.id to entity))
        constructor(entities: Collection<T>): this(entities.associateBy { it.id })

        override val type: CrudEvent.Type = CrudEvent.Type.DELETE
    }
}