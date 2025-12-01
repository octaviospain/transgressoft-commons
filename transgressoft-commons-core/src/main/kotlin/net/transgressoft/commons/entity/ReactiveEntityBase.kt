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

import net.transgressoft.commons.event.FlowEventPublisher
import net.transgressoft.commons.event.MutationEvent
import net.transgressoft.commons.event.MutationEvent.Type.MUTATE
import net.transgressoft.commons.event.ReactiveMutationEvent
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
 * @see MutationEvent
 */
abstract class ReactiveEntityBase<K, R : ReactiveEntity<K, R>>(
    private val publisher: TransEventPublisher<MutationEvent.Type, MutationEvent<K, R>>
) : ReactiveEntity<K, R> where K : Comparable<K> {
    private val log = KotlinLogging.logger {}

    protected constructor() : this(FlowEventPublisher("ReactiveEntity"))

    init {
        // A reactive entity only emits MUTATE events because it
        // cannot create, delete, or read itself
        publisher.activateEvents(MUTATE)
    }

    /**
     * The timestamp when this entity was last modified.
     * Automatically updated whenever a property is changed via [mutateAndPublish].
     */
    override var lastDateModified: LocalDateTime = LocalDateTime.now()
        protected set

    override val changes: SharedFlow<MutationEvent<K, R>> = publisher.changes

    override fun emitAsync(event: MutationEvent<K, R>) = publisher.emitAsync(event)

    override fun subscribe(action: suspend (MutationEvent<K, R>) -> Unit):
        TransEventSubscription<in TransEntity, MutationEvent.Type, MutationEvent<K, R>> = publisher.subscribe(action)

    override fun subscribe(subscriber: Flow.Subscriber<in MutationEvent<K, R>>?) = publisher.subscribe(subscriber)

    override fun subscribe(vararg eventTypes: MutationEvent.Type, action: Consumer<in MutationEvent<K, R>>):
        TransEventSubscription<in R, MutationEvent.Type, MutationEvent<K, R>> {
        require(MUTATE in eventTypes) {
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
    protected fun <T> mutateAndPublish(newValue: T, oldValue: T, propertySetAction: (T) -> Unit = {}) {
        if (newValue != oldValue) {
            val entityBeforeChange = clone()
            propertySetAction(newValue)
            lastDateModified = LocalDateTime.now()
            log.trace { "Firing entity update event from $entityBeforeChange to $this" }
            publisher.emitAsync(ReactiveMutationEvent(this as R, entityBeforeChange as R))
        }
    }

    @Suppress("UNCHECKED_CAST")
    protected fun <T> mutateAndPublish(mutationAction: () -> T): T {
        val entityBeforeChange = clone()
        val result = mutationAction()
        if (entityBeforeChange == this) {
            log.warn {
                "Attempt to publish update event from a mutation when object comparison was false. " +
                    "Consider implementing equals() and hashcode() that implies a mutation in instance variables affected by the mutationAction"
            }
        } else {
            lastDateModified = LocalDateTime.now()
            log.trace { "Firing entity update event from $entityBeforeChange to $this" }
            publisher.emitAsync(ReactiveMutationEvent(this as R, entityBeforeChange as R))
        }
        return result
    }
}