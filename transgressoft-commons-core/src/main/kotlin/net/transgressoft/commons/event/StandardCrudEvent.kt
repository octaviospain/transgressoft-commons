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

    internal data class Create<K, T: IdentifiableEntity<K>>(override val entities: Map<K, T>): CrudEvent<K, T> where K: Comparable<K> {
        override val type: CrudEvent.Type = CrudEvent.Type.CREATE
    }

    internal data class Read<K, T: IdentifiableEntity<K>>(override val entities: Map<K, T>): CrudEvent<K, T> where K: Comparable<K> {
        override val type: CrudEvent.Type = CrudEvent.Type.READ
    }

    internal data class Update<K, T: IdentifiableEntity<K>>(override val entities: Map<K, T>, override val oldEntities: Map<K, T>):
        EntityChangeEvent<K, T> where K: Comparable<K> {
        override val type: CrudEvent.Type = CrudEvent.Type.UPDATE
    }

    internal data class Delete<K, T: IdentifiableEntity<K>>(override val entities: Map<K, T>): CrudEvent<K, T> where K: Comparable<K> {
        override val type: CrudEvent.Type = CrudEvent.Type.DELETE
    }
}

fun CrudEvent<*, out IdentifiableEntity<*>>.isCreate(): Boolean = type == CrudEvent.Type.CREATE

fun CrudEvent<*, out IdentifiableEntity<*>>.isRead(): Boolean = type == CrudEvent.Type.READ

fun CrudEvent<*, out IdentifiableEntity<*>>.isUpdate(): Boolean = type == CrudEvent.Type.UPDATE

fun CrudEvent<*, out IdentifiableEntity<*>>.isDelete(): Boolean = type == CrudEvent.Type.DELETE

fun <K : Comparable<K>, T: IdentifiableEntity<K>> CrudEvent.Type.of(entity: T, oldEntity: T): CrudEvent<K, T> =
    this.of(mapOf(entity.id to entity), mapOf(oldEntity.id to oldEntity))

fun <K : Comparable<K>, T: IdentifiableEntity<K>> CrudEvent.Type.of(entity: T): CrudEvent<K, T> =
    this.of(mapOf(entity.id to entity))

fun <K: Comparable<K>, T: IdentifiableEntity<K>> CrudEvent.Type.of(entities: Map<K, T>, oldEntities: Map<K, T> = emptyMap()): CrudEvent<K, T> {
    if (this == CrudEvent.Type.UPDATE) {
        require(eventCollectionsAreConsistent(entities, oldEntities)) {
            "The collections of entities and old entities must be consistent for an UPDATE event. " +
                "They don't have the same size or they don't have the same keys."
        }
    }

    return when (this) {
        CrudEvent.Type.CREATE -> StandardCrudEvent.Create(entities)
        CrudEvent.Type.READ -> StandardCrudEvent.Read(entities)
        CrudEvent.Type.UPDATE -> StandardCrudEvent.Update(entities, oldEntities)
        CrudEvent.Type.DELETE -> StandardCrudEvent.Delete(entities)
    }
}

private fun eventCollectionsAreConsistent(entities: Map<*, *>, oldEntities: Map<*, *>): Boolean =
    entities.keys.containsAll(oldEntities.keys) && oldEntities.keys.containsAll(entities.keys) && entities.size == oldEntities.size