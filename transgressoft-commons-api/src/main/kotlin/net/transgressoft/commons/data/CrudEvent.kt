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

import net.transgressoft.commons.EntityChangeEvent
import net.transgressoft.commons.EventType
import net.transgressoft.commons.IdentifiableEntity
import net.transgressoft.commons.TransEvent
import net.transgressoft.commons.data.StandardCrudEvent.Type
import net.transgressoft.commons.data.StandardCrudEvent.Type.CREATE
import net.transgressoft.commons.data.StandardCrudEvent.Type.DELETE
import net.transgressoft.commons.data.StandardCrudEvent.Type.READ
import net.transgressoft.commons.data.StandardCrudEvent.Type.UPDATE

/**
 * Represents a [TransEvent] that carries a collection of [IdentifiableEntity] objects
 * related to CRUD (Create, Read, Update, Delete) operations.
 *
 * CrudEvent serves as the base event type for all entity operations in the system,
 * providing a standardized way to carry entity data through the event pipeline.
 * These events typically originate from repository operations and are published
 * to subscribers interested in entity changes.
 *
 * @param K the type of the [IdentifiableEntity] objects' id, which must be [Comparable]
 * @param T the type of the [IdentifiableEntity] objects
 */
interface CrudEvent<K, T: IdentifiableEntity<K>>: TransEvent where K: Comparable<K> {
    override val entities: Map<K, T>
}

/**
 * Container class that provides standard implementations of [CrudEvent] for
 * different CRUD operation types.
 *
 * This sealed class hierarchically organizes the different types of CRUD operations
 * and provides factory methods to create appropriately typed event instances.
 */
sealed class StandardCrudEvent {
    enum class Type(override val code: Int): EventType {
        CREATE(100),
        READ(200),
        UPDATE(300),
        DELETE(900);

        override fun toString() = "StandardDataEvent($name, $code)"
    }

    internal data class Create<K, T: IdentifiableEntity<K>>(override val entities: Map<K, T>): CrudEvent<K, T> where K: Comparable<K> {
        override val type: EventType = CREATE
    }

    internal data class Read<K, T: IdentifiableEntity<K>>(override val entities: Map<K, T>): CrudEvent<K, T> where K: Comparable<K> {
        override val type: EventType = READ
    }

    internal data class Update<K, T: IdentifiableEntity<K>>(override val entities: Map<K, T>, override val oldEntities: Map<K, T>):
        EntityChangeEvent<K, T> where K: Comparable<K> {
        override val type: EventType = UPDATE
    }

    internal data class Delete<K, T: IdentifiableEntity<K>>(override val entities: Map<K, T>): CrudEvent<K, T> where K: Comparable<K> {
        override val type: EventType = DELETE
    }
}

fun CrudEvent<*, out IdentifiableEntity<*>>.isCreate(): Boolean = type == CREATE

fun CrudEvent<*, out IdentifiableEntity<*>>.isRead(): Boolean = type == READ

fun CrudEvent<*, out IdentifiableEntity<*>>.isUpdate(): Boolean = type == UPDATE

fun CrudEvent<*, out IdentifiableEntity<*>>.isDelete(): Boolean = type == DELETE

fun <K : Comparable<K>, T: IdentifiableEntity<K>> Type.of(entity: T, oldEntity: T): CrudEvent<K, T> =
    this.of(mapOf(entity.id to entity), mapOf(oldEntity.id to oldEntity))

fun <K: Comparable<K>, T: IdentifiableEntity<K>> Type.of(entities: Map<K, T>, oldEntities: Map<K, T> = emptyMap()): CrudEvent<K, T> {
    if (this == UPDATE) {
        require(eventCollectionsAreConsistent(entities, oldEntities)) {
            """
            The collections of entities and old entities must be consistent for an UPDATE event.
            They don't have the same size or they don't have the same keys.
            """
        }
    }

    return when (this) {
        CREATE -> StandardCrudEvent.Create(entities)
        READ -> StandardCrudEvent.Read(entities)
        UPDATE -> StandardCrudEvent.Update(entities, oldEntities)
        DELETE -> StandardCrudEvent.Delete(entities)
    }
}

private fun eventCollectionsAreConsistent(entities: Map<*, *>, oldEntities: Map<*, *>): Boolean =
    entities.keys.containsAll(oldEntities.keys) && oldEntities.keys.containsAll(entities.keys) && entities.size == oldEntities.size