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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

object ReactiveScope {
    // Default scope with limited parallelism to prevent resource exhaustion
    // but ensuring all entity events are processed
    private var defaultFlowScope: CoroutineScope = CoroutineScope(Dispatchers.Default.limitedParallelism(4) + SupervisorJob())

    private var defaultIoScope: CoroutineScope = CoroutineScope(Dispatchers.IO.limitedParallelism(1) + SupervisorJob())

    /**
     * Sets the default scope for all reactive entities that don't specify their own.
     * Primarily used for testing to inject test dispatchers.
     *
     * @param scope The coroutine scope to use as default
     */
    fun setDefaultFlowScope(scope: CoroutineScope) {
        defaultFlowScope = scope
    }

    fun setDefaultIoScope(scope: CoroutineScope) {
        defaultIoScope = scope
    }

    /**
     * Gets the current default scope for reactive entities.
     *
     * @return The current default CoroutineScope
     */
    internal fun flowScope(): CoroutineScope = defaultFlowScope

    internal fun ioScope(): CoroutineScope = defaultIoScope
}