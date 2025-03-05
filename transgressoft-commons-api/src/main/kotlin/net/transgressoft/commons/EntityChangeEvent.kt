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

import net.transgressoft.commons.data.CrudEvent

/**
 * Represents a [CrudEvent] that tracks changes to entities by maintaining both
 * the current state and the previous state of affected entities.
 *
 * This event type is particularly useful for update operations where understanding
 * what changed between states is important for subscribers. It allows subscribers
 * to react to specific field changes or state transitions rather than just the
 * final state.
 *
 * @param K the type of the [IdentifiableEntity] objects' id, which must be [Comparable]
 * @param T the type of the [IdentifiableEntity] objects
 */
interface EntityChangeEvent<K, T : IdentifiableEntity<K>> : CrudEvent<K, T> where K : Comparable<K> {
    val oldEntities: Map<K, T>
}