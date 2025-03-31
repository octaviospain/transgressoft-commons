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

import net.transgressoft.commons.entity.TransEntity
import mu.KotlinLogging
import java.util.concurrent.ConcurrentSkipListSet
import java.util.concurrent.Flow
import java.util.function.Consumer
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Class that provides reactive event publishing capabilities using Kotlin coroutine flows.
 *
 * `FlowEventPublisher` implements the core functionality needed for reactive programming by:
 * 1. Managing a [MutableSharedFlow] to broadcast events to multiple subscribers
 * 2. Providing compatibility with both Java's [Flow.Subscriber] API and Kotlin's flow-based approach
 * 3. Supporting suspending functions for asynchronous event handling
 *
 * This class serves as a foundational layer for both entity-level reactivity (property changes)
 * and collection-level reactivity (CRUD operations) within the reactive architecture.
 *
 * Key features:
 * - Thread-safe event publication with backpressure handling
 * - Support for both traditional subscribers and modern flow collectors
 * - Coroutine-based asynchronous event processing
 * - Selective event publishing based on event type activation
 *
 * @param E The specific type of [TransEvent] this publisher will emit
 *
 * @see [TransEventPublisher]
 * @see [SharedFlow]
 */
class FlowEventPublisher<E: TransEvent>(id: String): TransEventPublisher<E> {

    private val log = KotlinLogging.logger {}

    private val name: String = "FlowEventPublisher-$id"

    /**
     * Channel for processing events with unlimited buffer capacity.
     *
     * This unlimited buffer ensures that events are never dropped during high-traffic
     * periods or bursts of activity. When the processing rate catches up after a burst,
     * the memory used by processed events becomes eligible for garbage collection.
     *
     * This approach prioritizes reliable event delivery over fixed memory constraints.
     */
    private val eventChannel = Channel<E>(Channel.UNLIMITED)

    // SharedFlow for entity change events with sufficient buffer and SUSPEND policy to ensure no events are lost
    private val changesFlow =
        MutableSharedFlow<E>(
            // Increasing this enables an 'object history' by replayed events. TBD
            replay = 0,
            // Larger buffer to handle bursts
            extraBufferCapacity = 5120,
            // Block until buffer space is available
            onBufferOverflow = BufferOverflow.SUSPEND
        )

    override val changes: SharedFlow<E> = changesFlow.asSharedFlow()

    /**
     * The coroutine scope used for emitting change events.
     */
    private val flowScope = ReactiveScope.flowScope()

    private var activatedEventTypes: MutableSet<EventType> = ConcurrentSkipListSet()

    init {
        log.trace { "FlowEventPublisher created: $name" }

        // Create a single persistent coroutine to handle all emissions for a fire and forget approach
        flowScope.launch {
            for (event in eventChannel) {
                try {
                    changesFlow.emit(event) // This suspends if needed
                } catch (exception: Exception) {
                    log.error(exception) { "Unexpected error during event emission: $event" }
                }
            }
        }
    }

    override fun emitAsync(event: E) {
        if (event.type in activatedEventTypes) {
            // Use trySend so we don't block the caller
            // If channel is full, this will return closed/failed result
            val result = eventChannel.trySend(event)
            if (!result.isSuccess) {
                log.warn { "Could not send event to channel, buffer full or closed: $event" }
            }
        }
    }

    /**
     * Legacy compatibility method to support the existing [Flow.Subscriber] interface.
     * Consider migrating to the Kotlin Flow-based subscription method instead.
     */
    override fun subscribe(subscriber: Flow.Subscriber<in E>) {
        log.trace { "Subscription registered to $subscriber" }

        val job =
            flowScope.launch {
                changesFlow.collectLatest { event ->
                    subscriber.onNext(event)
                }
            }

        subscriber.onSubscribe(ReactiveSubscription<TransEntity>(this, job))
    }

    /**
     * Subscribes to entity change events by providing an action to execute when changes occur.
     *
     * @param action The action to execute when the entity changes
     * @return A subscription that can be used to unsubscribe
     */
    override fun subscribe(action: suspend (E) -> Unit): TransEventSubscription<in TransEntity> {
        log.trace { "Anonymous subscription registered on $name" }

        // Each subscription requires its own collection coroutine to handle events independently
        // This is a deliberate design pattern for reactive subscriptions
        @Suppress("kotlin:S6311")
        val job =
            flowScope.launch {
                changesFlow.collectLatest { event ->
                    action(event)
                }
            }
        return ReactiveSubscription(this, job)
    }

    /**
     * Legacy compatibility method for Java-style Consumer subscriptions.
     * Consider migrating to the Kotlin Flow-based subscription method instead.
     */
    override fun subscribe(action: Consumer<in E>): TransEventSubscription<in TransEntity> = subscribe { event -> action.accept(event) }

    override fun disableEvents(vararg types: EventType) {
        types.toSet().let {
            activatedEventTypes.removeAll(it)
            log.trace { "Enabled event types from $name: $activatedEventTypes" }
        }
    }

    override fun activateEvents(vararg types: EventType) {
        types.toSet().let {
            activatedEventTypes.addAll(it)
            log.trace { "Enabled event types from $name: $activatedEventTypes" }
        }
    }

    override fun toString() = "FlowEventPublisher(id=$name, activatedEventTypes=$activatedEventTypes)"

    inner class ReactiveSubscription<T: TransEntity>(override val source: TransEventPublisher<E>, private val job: Job): TransEventSubscription<T> {

        override fun request(n: Long) {
            error("Events cannot be requested on demand")
        }

        override fun cancel() {
            job.cancel()
        }
    }
}