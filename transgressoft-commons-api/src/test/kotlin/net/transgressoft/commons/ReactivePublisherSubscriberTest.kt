package net.transgressoft.commons

import net.transgressoft.commons.data.StandardCrudEvent.Type.UPDATE
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.confirmVerified
import io.mockk.spyk
import io.mockk.verify
import java.util.concurrent.Flow
import kotlin.time.Duration.Companion.milliseconds

data class Tenant(override val id: Int): ReactiveEntityBase<Int, Tenant>() {

    var name: String? = null
        set(value) {
            setAndNotify(value, field) { field = it }
        }

    override fun subscribe(subscriber: Flow.Subscriber<in EntityChangeEvent<Int, Tenant>>) {
        super.subscribe(subscriber)
        val subscription = EntityChangeSubscription(this) { unsubscribe(subscriber) }
        subscriber.onSubscribe(subscription)
        spiedSubscription = spyk(subscription)
    }

    override val uniqueId = "$id-$name"

    override fun clone(): Tenant = copy()
}

lateinit var spiedSubscription: EntityChangeSubscription<Int, Tenant>

internal class ReactivePublisherSubscriberTest: StringSpec({

    val reactiveEntity = spyk(Tenant(1))

    var actualEvent: EntityChangeEvent<Int, Tenant>? = null

    val subscriber =
        EntityChangeSubscriber<Tenant, EntityChangeEvent<Int, Tenant>, Int>("subscriber").apply {
            addOnNextEventAction(UPDATE) {
                actualEvent = it
            }
        }

    "Subscriber receives events from the reactive entity when updated" {
        reactiveEntity.name.shouldBeNull()

        reactiveEntity.subscribe(subscriber)

        reactiveEntity.name = "Octavio"

        eventually(100.milliseconds) {
            actualEvent should {
                it shouldNotBe null
                it!!.type shouldBe UPDATE
                it.entities should { entitiesMap ->
                    entitiesMap.size shouldBe 1
                    entitiesMap[1] shouldBe reactiveEntity
                    entitiesMap[1]!!.name shouldBe "Octavio"
                }
                it.oldEntities should { entitiesMap ->
                    entitiesMap.size shouldBe 1
                    entitiesMap[1] shouldBe reactiveEntity.copy()
                    entitiesMap[1]!!.name.shouldBeNull()
                }
            }
        }
        val actualEventHash = actualEvent.hashCode()
        spiedSubscription.cancel()

        reactiveEntity.name = "Mario"

        reactiveEntity.name shouldBe "Mario"

        actualEvent.hashCode() shouldBe actualEventHash

        verify(exactly = 3) { reactiveEntity.name }
        verify(exactly = 1) { reactiveEntity.subscribe(any()) }

        verify { spiedSubscription.cancel() }
        verify { spiedSubscription.source }
        confirmVerified(spiedSubscription)
    }
})