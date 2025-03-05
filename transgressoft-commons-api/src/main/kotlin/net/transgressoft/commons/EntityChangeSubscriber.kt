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
 * A general-purpose subscriber implementation for entity change events.
 *
 * This class provides a ready-to-use subscriber that can be configured with
 * custom actions for different event types through the methods inherited from
 * [TransEventSubscriberBase].
 *
 * @param R The type of reactive entity this subscriber handles
 * @param E The type of entity change events this subscriber processes
 * @param K The type of the entity's identifier
 * @param name A descriptive name for this subscriber, used in logging and debugging
 */
class EntityChangeSubscriber<R : ReactiveEntity<K, R>, E : EntityChangeEvent<K, R>, K>(name: String) :
    TransEventSubscriberBase<R, E>(name) where K : Comparable<K>