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

package net.transgressoft.commons.entity

import net.transgressoft.commons.event.CrudEvent
import net.transgressoft.commons.event.CrudEvent.Type.UPDATE
import net.transgressoft.commons.event.EntityChangeEvent
import net.transgressoft.commons.event.FlowEventPublisher
import net.transgressoft.commons.event.StandardCrudEvent.Update
import net.transgressoft.commons.event.TransEventPublisher
import net.transgressoft.commons.event.TransEventSubscription
import mu.KotlinLogging
import java.time.LocalDateTime
import java.util.concurrent.Flow
import java.util.function.Consumer
import kotlinx.coroutines.flow.SharedFlow

/**
 * Abstract base class that provides reactive functionality for entities, enabling them to notify subscribers
 * about property changes through a reactive flow-based pattern.
 *
 * This class implements the [ReactiveEntity] interface and manages subscriptions using Kotlin Flows.
 * When properties of the entity change, all subscribers are automatically notified with both the updated
 * entity state and the previous state.
 *
 * @param K The type of the entity's unique identifier, which must implement [Comparable]
 * @param R The concrete type of the reactive entity that extends this class
 *
 * @see ReactiveEntity
 * @see EntityChangeEvent
 */
abstract class ReactiveEntityBase<K, R : ReactiveEntity<K, R>>(
    private val publisher: TransEventPublisher<CrudEvent.Type, EntityChangeEvent<K, R>>
) : ReactiveEntity<K, R> where K : Comparable<K> {
    private val log = KotlinLogging.logger {}

    protected constructor() : this(FlowEventPublisher("ReactiveEntity"))

    init {
        // A reactive entity only emits UPDATE events because it
        // cannot create, delete, or read itself
        publisher.activateEvents(UPDATE)
    }

    /**
     * The timestamp when this entity was last modified.
     * Automatically updated whenever a property is changed via [setAndNotify].
     */
    override var lastDateModified: LocalDateTime = LocalDateTime.now()
        protected set

    override val changes: SharedFlow<EntityChangeEvent<K, R>> = publisher.changes

    override fun emitAsync(event: EntityChangeEvent<K, R>) = publisher.emitAsync(event)

    override fun subscribe(action: suspend (EntityChangeEvent<K, R>) -> Unit):
        TransEventSubscription<in TransEntity, CrudEvent.Type, EntityChangeEvent<K, R>> = publisher.subscribe(action)

    override fun subscribe(subscriber: Flow.Subscriber<in EntityChangeEvent<K, R>>?) = publisher.subscribe(subscriber)

    override fun subscribe(vararg eventTypes: CrudEvent.Type, action: Consumer<in EntityChangeEvent<K, R>>):
        TransEventSubscription<in R, CrudEvent.Type, EntityChangeEvent<K, R>> {
        require(UPDATE in eventTypes) {
            throw IllegalArgumentException("Only UPDATE event is supported for reactive entities")
        }
        return subscribe(action::accept)
    }

    /**
     * Sets a property value and notifies all subscribers if the value has changed.
     *
     * This method implements the reactive pattern - it:
     * 1. Compares the new value with the old value
     * 2. If different, captures the entity state before the change
     * 3. Applies the new value using the provided property setter
     * 4. Updates the last modified timestamp
     * 5. Notifies all subscribers with both the updated and previous entity states
     *
     * @param T The type of the property being modified
     * @param newValue The new value to set
     * @param oldValue The current value of the property
     * @param propertySetAction A consumer that actually sets the property's value
     */
    @Suppress("UNCHECKED_CAST")
    @JvmOverloads
    protected fun <T> setAndNotify(newValue: T, oldValue: T, propertySetAction: Consumer<T> = Consumer { }) {
        if (newValue != oldValue) {
            val entityBeforeChange = clone()
            propertySetAction.accept(newValue)
            lastDateModified = LocalDateTime.now()
            log.trace { "Firing entity update event from $entityBeforeChange to $this" }
            publisher.emitAsync(Update(this, entityBeforeChange) as EntityChangeEvent<K, R>)
        }
    }
}