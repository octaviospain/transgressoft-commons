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

package net.transgressoft.commons

import net.transgressoft.commons.data.StandardCrudEvent.Type.UPDATE
import net.transgressoft.commons.data.of
import mu.KotlinLogging
import java.time.LocalDateTime
import java.util.concurrent.Executors
import java.util.concurrent.Flow
import java.util.function.Consumer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch

/**
 * Abstract base class that provides reactive functionality for entities, enabling them to notify subscribers
 * about property changes through a publish-subscribe pattern.
 *
 * This class implements the [ReactiveEntity] interface and manages subscriptions from interested parties.
 * When properties of the entity change, all subscribers are automatically notified with both the updated
 * entity state and the previous state.
 *
 * @param K The type of the entity's unique identifier, which must implement [Comparable]
 * @param R The concrete type of the reactive entity that extends this class
 *
 * @see ReactiveEntity
 * @see Flow.Subscriber
 * @see EntityChangeEvent
 */
abstract class ReactiveEntityBase<K, R : ReactiveEntity<K, R>> : ReactiveEntity<K, R> where K : Comparable<K> {
    private val log = KotlinLogging.logger {}
    private val entityLog = KotlinLogging.logger(javaClass.name)

    private val subscribers: MutableSet<Flow.Subscriber<in EntityChangeEvent<K, R>>> = mutableSetOf()

    companion object {
        private val reactiveEntityDispatcher: CoroutineDispatcher by lazy { Executors.newCachedThreadPool().asCoroutineDispatcher() }
        private val reactiveEntityEventsScope: CoroutineScope = CoroutineScope(reactiveEntityDispatcher)
    }

    override var lastDateModified: LocalDateTime = LocalDateTime.now()
        protected set

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
            val entityBeforeChange = clone() as R
            propertySetAction.accept(newValue)
            lastDateModified = LocalDateTime.now()
            notifySubscribers(this as R, entityBeforeChange)
        }
    }

    /**
     * Notifies all subscribers about changes to the entity.
     *
     * This method is called internally when entity properties change and handles the
     * asynchronous distribution of update events to all registered subscribers. Each
     * subscriber receives an update event containing both the updated entity and its
     * previous state, allowing subscribers to react to specific changes.
     *
     * The notification occurs asynchronously using a dedicated coroutine scope to
     * prevent blocking the main thread during subscriber notifications.
     *
     * @param updatedEntity The current state of the entity after changes
     * @param oldEntity The previous state of the entity before changes
     */
    private fun notifySubscribers(updatedEntity: R, oldEntity: R) {
        reactiveEntityEventsScope.launch {
            subscribers.forEach {
                log.trace { "Firing entity update event from $oldEntity to $updatedEntity towards $it" }
                it.onNext(UPDATE.of(mapOf(updatedEntity.id to updatedEntity), mapOf(oldEntity.id to oldEntity)) as EntityChangeEvent<K, R>)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun subscribe(subscriber: Flow.Subscriber<in EntityChangeEvent<K, R>>) {
        entityLog.trace { "Subscription registered to $subscriber" }
        subscribers.add(subscriber)
        subscriber.onSubscribe(EntityChangeSubscription(this@ReactiveEntityBase as R) { unsubscribe(subscriber) })
    }

    protected open fun unsubscribe(subscriber: Flow.Subscriber<in EntityChangeEvent<K, R>>) {
        entityLog.trace { "Subscription removed from $subscriber" }
        subscribers.remove(subscriber)
    }
}