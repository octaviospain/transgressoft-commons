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

/**
 * An event that can be published and subscribed to within the reactive event system.
 *
 * TransEvent serves as the foundation for all events in the system, providing
 * a common interface that all specific event types must implement. This facilitates
 * a consistent approach to event handling, publishing, and subscription.
 */
interface TransEvent {

    /**
     * The type of this event, used to categorize and filter events.
     *
     * Event types allow subscribers to selectively process only events they're
     * interested in, based on the event's category.
     */
    val type: EventType

    /**
     * A map of entities associated with this event, keyed by their identifiers.
     *
     * This contains the entities that are relevant to or affected by the event.
     * The specific meaning depends on the concrete event implementation.
     */
    val entities: Map<*, TransEntity>
}