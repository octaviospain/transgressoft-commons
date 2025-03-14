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

/**
 * Base implementation of [TransEventPublisher] that provides event filtering and activation capabilities.
 *
 * `TransEventPublisherBase` serves as a foundational layer for publishing various types of events
 * in the reactive architecture. It handles the common concerns of event publishers including:
 *
 * - Selective event publishing based on event type activation
 * - Filtering events before publication
 * - Logging event publishing activities
 *
 * This class is designed to be extended by domain-specific publishers that need to handle
 * particular event types, such as CRUD operations or custom domain events.
 *
 * @param E The specific type of [TransEvent] this publisher will emit
 * @param name A descriptive name for this publisher, used in logging and debugging
 *
 * @see TransEventPublisher
 * @see EventType
 */
abstract class TransEventPublisherBase<E : TransEvent>(
    protected val name: String,
    protected val publisher: TransEventPublisher<E> = FlowEventPublisher<E>()
) : TransEventPublisher<E> by publisher {

    private val log = KotlinLogging.logger {}

    private var activatedEventTypes: MutableSet<EventType> = mutableSetOf()

    protected fun putEventAction(event: E) {
        if (activatedEventTypes.contains(event.type)) {
            publisher.emitAsync(event)
        }
    }

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

    override fun toString() = "TransEventPublisher(name=$name, activatedEventTypes=$activatedEventTypes)"
}