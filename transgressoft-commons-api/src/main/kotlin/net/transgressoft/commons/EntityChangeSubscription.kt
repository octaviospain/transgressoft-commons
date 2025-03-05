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
import java.util.function.Consumer

/**
 * Standard implementation of [ReactiveEntitySubscription] that manages the subscription
 * lifecycle for entity change events.
 *
 * This class handles subscription cancellation and enforces the push-based nature
 * of entity change events by preventing on-demand requests.
 *
 * @param K The type of the entity's identifier
 * @param R The type of reactive entity that is the source of events
 * @property source The reactive entity that is the source of events
 * @property cancelSubscriptionAction Action to execute when the subscription is cancelled
 */
open class EntityChangeSubscription<K, R : ReactiveEntity<K, R>>(
    override val source: R,
    private val cancelSubscriptionAction: Consumer<EntityChangeSubscription<K, R>> = Consumer { }
) : ReactiveEntitySubscription<EntityChangeEvent<K, R>, R, K> where K : Comparable<K> {

    private val log = KotlinLogging.logger(javaClass.name)

    /**
     * This implementation explicitly prohibits requesting items on demand.
     *
     * Since this subscription operates on a push-based model where data events occur in response to
     * repository operations rather than subscriber requests, this method intentionally throws
     * an exception to prevent misuse of the subscription pattern.
     *
     * @param n The number of items requested (not used)
     * @throws IllegalStateException Always thrown to indicate this operation is not supported
     */
    override fun request(n: Long): Unit = throw IllegalStateException("Instances of EntityChangeEvent cannot be requested on demand")

    /**
     * Cancels this subscription and notifies the publisher.
     *
     * This method executes the [cancelSubscriptionAction] provided during construction,
     * which typically removes this subscription from the publisher's registry of active subscribers.
     */
    override fun cancel() {
        log.trace { "Subscription to publisher ${source.uniqueId} cancelled" }
        cancelSubscriptionAction.accept(this)
    }
}