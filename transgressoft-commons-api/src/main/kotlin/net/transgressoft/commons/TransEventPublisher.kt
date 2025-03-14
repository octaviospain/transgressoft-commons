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
import java.util.function.Consumer
import kotlinx.coroutines.flow.SharedFlow

/**
 * A publisher of [TransEvent]s that implements the reactive streams [Flow.Publisher] interface.
 *
 * This interface represents the source of events in the reactive stream, publishing
 * events to interested subscribers. It serves as a bridge between the standard
 * Java Flow API and transgressoft-commons event system.
 *
 * @param E The specific type of [TransEvent] published by this publisher
 */
interface TransEventPublisher<E : TransEvent> : Flow.Publisher<E> {

    /**
     * A flow of entity change events that can be observed by collectors.
     */
    val changes: SharedFlow<E>

    /**
     * Publishes an event to all subscribers, asynchronously.
     */
    fun emitAsync(event: E)

    fun subscribe(action: suspend (E) -> Unit): TransEventSubscription<in TransEntity>

    fun subscribe(action: Consumer<in E>): TransEventSubscription<in TransEntity>
}