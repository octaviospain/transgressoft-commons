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

import mu.KotlinLogging
import java.util.concurrent.Executors
import java.util.concurrent.Flow
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch

/**
 * Base implementation of [TransEventPublisher] that provides common functionality
 * for all event publishers in the system.
 *
 * This class manages subscriptions, controls event activation/deactivation by type,
 * and handles the asynchronous distribution of events to subscribers. It provides
 * the core infrastructure for event-based communication in the system.
 *
 * @param E The specific type of [TransEvent] published by this publisher
 * @param name A descriptive name for this publisher, used in logging and debugging
 *
 * @see [TransEventSubscriber]
 */
abstract class TransEventPublisherBase<E : TransEvent>(protected val name: String) : TransEventPublisher<E> {

    private val log = KotlinLogging.logger {}

    private val subscribers: MutableSet<Flow.Subscriber<in E>> = mutableSetOf()

    private var activatedEventTypes: MutableSet<EventType> = mutableSetOf()

    companion object {
        private val eventsPublisherDispatcher: CoroutineDispatcher by lazy { Executors.newCachedThreadPool().asCoroutineDispatcher() }
        private val eventsPublisherScope: CoroutineScope = CoroutineScope(eventsPublisherDispatcher)
    }

    protected fun putEventAction(event: E) {
        if (isEventTypeActive(event.type)) {
            eventsPublisherScope.launch {
                subscribers.forEach {
                    log.trace { "Firing $event on subscriber $it from $name" }
                    it.onNext(event)
                }
            }
        }
    }

    protected open fun putCompleteEvent() {
        eventsPublisherScope.launch {
            subscribers.forEach {
                log.trace { "onComplete event fired from $name" }
                it.onComplete()
            }
            subscribers.clear()
        }
    }

    protected fun isEventTypeActive(type: EventType) = activatedEventTypes.contains(type)

    protected fun disableEvents(vararg types: EventType) {
        types.toSet().let {
            activatedEventTypes.removeAll(it)
            log.trace { "Event Types $it disabled from $name. Current active event types: $activatedEventTypes" }
        }
    }

    protected fun activateEvents(vararg types: EventType) {
        types.toSet().let {
            activatedEventTypes.addAll(it)
            log.trace { "Event Types $it enabled from $name. Current active event types: $activatedEventTypes" }
        }
    }

    override fun subscribe(subscriber: Flow.Subscriber<in E>) {
        log.trace { "$name registered a subscription to $subscriber" }
        subscribers.add(subscriber)
    }

    protected open fun unsubscribe(subscriber: Flow.Subscriber<in E>) {
        log.trace { "$name removed the subscription to $subscriber" }
        subscribers.remove(subscriber)
    }

    override fun toString() = "TransEventPublisher(name=$name, subscribers=${subscribers.size}, activatedEventTypes=$activatedEventTypes)"
}