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
import net.transgressoft.commons.event.FlowEventPublisher
import net.transgressoft.commons.event.StandardCrudEvent
import net.transgressoft.commons.event.StandardCrudEvent.Type.CREATE
import net.transgressoft.commons.event.StandardCrudEvent.Type.DELETE
import net.transgressoft.commons.event.StandardCrudEvent.Type.READ
import net.transgressoft.commons.event.StandardCrudEvent.Type.UPDATE
import net.transgressoft.commons.event.TransEventPublisher
import net.transgressoft.commons.event.TransEventSubscription
import net.transgressoft.commons.event.of
import java.util.concurrent.Flow
import java.util.function.Consumer

/**
 * Base publisher implementation specialized for CRUD events related to identifiable entities.
 *
 * This class is composed by a [FlowEventPublisher] with convenience methods for publishing
 * specific types of CRUD events (create, read, update, delete). By default, it activates
 * CREATE, UPDATE, and DELETE event types, making them ready to be published without
 * additional configuration.
 *
 * @param K The type of the entity's identifier
 * @param T The type of identifiable entity this publisher handles
 * @param name A descriptive name for this publisher, used in logging and debugging
 *
 * @see [CrudEvent]
 * @see [FlowEventPublisher]
 */
abstract class CrudEventPublisherBase<K : Comparable<K>, T : IdentifiableEntity<K>>(
    name: String,
    private val publisher: TransEventPublisher<CrudEvent<K, T>> = FlowEventPublisher(name)
) : TransEventPublisher<CrudEvent<K, T>> by publisher {

    init {
        activateEvents(CREATE, UPDATE, DELETE)
    }

    final override fun subscribe(subscriber: Flow.Subscriber<in CrudEvent<K, T>>) {
        publisher.subscribe(subscriber)
    }

    fun subscribe(crudEventType: StandardCrudEvent.Type, action: Consumer<CrudEvent<K, T>>): TransEventSubscription<in T> =
        when (crudEventType) {
            CREATE -> addCreateEventSubscriber { action.accept(it) }
            READ -> addReadEventSubscriber { action.accept(it) }
            UPDATE -> addUpdateEventSubscriber { action.accept(it) }
            DELETE -> addDeleteEventSubscriber { action.accept(it) }
        }

    /**
     * Publishes a CREATE event for a single entity.
     *
     * @param entity The entity that was created
     */
    protected open fun putCreateEvent(entity: T) = emitAsync(CREATE.of(entity))

    /**
     * Publishes a CREATE event for a collection of entities.
     *
     * @param entities The collection of entities that were created
     */
    protected open fun putCreateEvent(entities: Collection<T>) = emitAsync(CREATE.of(entities.toMapById()))

    /**
     * Publishes a READ event for a single entity.
     *
     * @param entity The entity that was read
     */
    protected open fun putReadEvent(entity: T) = emitAsync(READ.of(entity))

    /**
     * Publishes a READ event for a collection of entities.
     *
     * @param entities The collection of entities that were read
     */
    protected open fun putReadEvent(entities: Collection<T>) = emitAsync(READ.of(entities.toMapById()))

    /**
     * Publishes an UPDATE event for a single entity, including its previous state.
     *
     * @param entity The updated entity in its current state
     * @param oldEntity The entity in its previous state before the update
     */
    protected open fun putUpdateEvent(entity: T, oldEntity: T) =
        emitAsync(
            UPDATE.of(
                mapOf(entity.id to entity),
                mapOf(
                    oldEntity.id to oldEntity
                )
            )
        )

    /**
     * Publishes an UPDATE event for a collection of entities, including their previous states.
     *
     * @param entities The collection of updated entities in their current state
     * @param oldEntities The collection of entities in their previous state before the update
     */
    protected open fun putUpdateEvent(entities: Collection<T>, oldEntities: Collection<T>) =
        emitAsync(UPDATE.of(entities.toMapById(), oldEntities.toMapById()))

    /**
     * Publishes a DELETE event for a single entity.
     *
     * @param entity The entity that was deleted
     */
    protected open fun putDeleteEvent(entity: T) = emitAsync(DELETE.of(entity))

    /**
     * Publishes a DELETE event for a collection of entities.
     *
     * @param entities The collection of entities that were deleted
     */
    protected open fun putDeleteEvent(entities: Collection<T>) = emitAsync(DELETE.of(entities.toMapById()))

    private fun Collection<T>.toMapById() = associateBy { it.id }

    private fun addCreateEventSubscriber(action: (CrudEvent<K, T>) -> Unit): TransEventSubscription<in T> =
        subscribe {
            if (it.type == CREATE) {
                action.invoke(it)
            }
        }

    private fun addUpdateEventSubscriber(action: (CrudEvent<K, T>) -> Unit): TransEventSubscription<in T> =
        subscribe {
            if (it.type == UPDATE) {
                action.invoke(it)
            }
        }

    private fun addReadEventSubscriber(action: (CrudEvent<K, T>) -> Unit): TransEventSubscription<in T> =
        subscribe {
            if (it.type == READ) {
                action.invoke(it)
            }
        }

    private fun addDeleteEventSubscriber(action: (CrudEvent<K, T>) -> Unit): TransEventSubscription<in T> =
        subscribe {
            if (it.type == DELETE) {
                action.invoke(it)
            }
        }
}