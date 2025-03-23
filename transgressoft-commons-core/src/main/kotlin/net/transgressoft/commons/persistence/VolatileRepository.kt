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

package net.transgressoft.commons.persistence

import net.transgressoft.commons.entity.IdentifiableEntity

/**
 * An in-memory repository implementation with no persistence.
 *
 * `VolatileRepository` provides a simple repository implementation that keeps all entities
 * in memory without any persistence mechanism. It's useful for temporary data storage,
 * testing scenarios, or cases where persistence isn't required.
 *
 * Since this repository is volatile, all data is lost when the application terminates
 * or when the repository instance is garbage collected.
 *
 * Example usage:
 * ```
 * class UserRepository : VolatileRepository("UserRepository")
 * ```
 *
 * @param K The type of entity identifier, must be [Comparable]
 * @param T The type of entity being stored, must implement [IdentifiableEntity]
 * @param name A descriptive name for this repository, used in logging
 * @param initialMap Optional map of entities to initialize the repository with
 */
abstract class VolatileRepository<K : Comparable<K>, T : IdentifiableEntity<K>>(
    name: String,
    initialMap: Map<K, T> = emptyMap()
) : RepositoryBase<K, T>(name, HashMap(initialMap))