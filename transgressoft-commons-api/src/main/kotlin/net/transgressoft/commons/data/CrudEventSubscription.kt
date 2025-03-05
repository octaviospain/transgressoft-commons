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

package net.transgressoft.commons.data

import net.transgressoft.commons.IdentifiableEntity
import net.transgressoft.commons.TransEventSubscription
import mu.KotlinLogging
import java.util.function.Consumer

/**
 * Standard implementation of [TransEventSubscription] for data events published by [CrudEventPublisherBase].
 *
 * Unlike traditional stream subscriptions that pull data on demand, this subscription is designed for
 * a push-based notification model. Data events are published automatically when entities are created,
 * read, updated, or deleted in a [Repository], rather than being requested by subscribers.
 *
 * @param K The type of the entity's identifier, which must be [Comparable]
 * @param T The type of the identifiable entity
 *
 * @property source The publisher that generates and sends data events to this subscription
 * @property cancelSubscriptionAction Action executed when the subscription is cancelled, typically
 *           used to remove this subscription from the publisher's registry
 *
 * @see [CrudEvent]
 */
abstract class CrudEventSubscription<K, T : IdentifiableEntity<K>>(
    override val source: CrudEventPublisherBase<K, T>,
    private val cancelSubscriptionAction: Consumer<CrudEventSubscription<K, T>> = Consumer { }
) : TransEventSubscription<T> where K : Comparable<K> {

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
    override fun request(n: Long): Unit = throw IllegalStateException("Instances of CrudEvent cannot be requested on demand")

    /**
     * Cancels this subscription and notifies the publisher.
     *
     * This method executes the [cancelSubscriptionAction] provided during construction,
     * which typically removes this subscription from the publisher's registry of active subscribers.
     */
    override fun cancel() {
        log.trace { "Subscription cancelled" }
        cancelSubscriptionAction.accept(this)
    }
}