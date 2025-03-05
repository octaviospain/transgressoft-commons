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

import java.time.LocalDateTime

/**
 * Represents an entity that can be reactive to changes in its properties. Reactive in the way that
 * regarding its internal logic, it can create a logic reaction on the subscribed entities.
 *
 * By extending from [TransEventPublisher] subscribers can be notified about changes in the form of [EntityChangeEvent]s.
 *
 * @param K the type of the entity's id.
 * @param R the type of the entity.
 */
interface ReactiveEntity<K, R : ReactiveEntity<K, R>> :
    IdentifiableEntity<K>,
    TransEventPublisher<EntityChangeEvent<K, R>> where K : Comparable<K> {
    val lastDateModified: LocalDateTime
}