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

package net.transgressoft.commons.persistence.json.primitives

import net.transgressoft.commons.persistence.ReactivePrimitiveWrapper
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * A reactive wrapper for Int values.
 *
 * This class enables Int values to participate in the reactive entity system,
 * with changes to the value triggering notification events to subscribers. It includes
 * custom serialization for JSON persistence.
 *
 * @property id The unique identifier for this reactive integer
 * @param initialValue The initial integer value, can be null
 */
class ReactiveInt internal constructor(id: String, initialValue: Int?) : ReactivePrimitiveWrapper<ReactiveInt, Int>(id, initialValue) {

    @OptIn(ExperimentalUuidApi::class)
    constructor(initialValue: Int) : this(Uuid.random().toString(), initialValue) {}

    override fun hashCode() = (id.hashCode() + value.hashCode()) * 31

    override fun equals(other: Any?) =
        when {
            this === other -> true
            other !is ReactiveInt -> false
            else -> id == other.id && value == other.value
        }

    override fun clone(): ReactiveInt = ReactiveInt(id, value)

    override fun toString() =
        buildString {
            append("ReactiveInt(")
            append("id=$id, ")
            append("value=$value")
            append(")")
        }
}