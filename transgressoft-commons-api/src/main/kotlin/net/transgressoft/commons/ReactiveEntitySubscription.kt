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

import java.util.concurrent.Flow

/**
 * Specialized subscription for reactive entities that connects subscribers to
 * entity change events from a specific reactive entity source.
 *
 * This interface extends the standard [Flow.Subscription] to provide access to the
 * specific reactive entity that is the source of entity change events.
 *
 * @param E The type of entity change events this subscription handles
 * @param R The type of reactive entity that is the source of events
 * @param K The type of the entity's identifier
 */
interface ReactiveEntitySubscription<E : EntityChangeEvent<K, R>, R : ReactiveEntity<K, R>, K> : Flow.Subscription where K : Comparable<K> {
    /**
     * The reactive entity that is the source of events for this subscription.
     *
     * This reference allows subscribers to interact with or query the source entity
     * if needed.
     */
    val source: R
}