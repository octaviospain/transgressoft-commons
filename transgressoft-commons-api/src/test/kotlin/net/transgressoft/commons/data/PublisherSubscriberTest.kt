package net.transgressoft.commons.data

import net.transgressoft.commons.IdentifiableEntity
import net.transgressoft.commons.TransEventSubscriberBase
import net.transgressoft.commons.data.StandardCrudEvent.Type.CREATE
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.assertions.throwables.shouldThrowMessage
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.confirmVerified
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import kotlin.time.Duration.Companion.milliseconds

internal class PublisherSubscriberTest: StringSpec({

    abstract class TestThing {

        abstract fun called()

        abstract fun error(throwable: Throwable)
    }

    data class TestEntity(override val id: Int, override val uniqueId: String = id.toString()): IdentifiableEntity<Int> {

        override fun clone(): TestEntity = copy()
    }

    class SomePublisherBase: CrudEventPublisherBase<Int, IdentifiableEntity<Int>>("SomePublisher") {

        fun create(expectedId: Int) {
            putCreateEvent(TestEntity(expectedId))
        }

        fun finishPublishing() {
            putCompleteEvent()
            disableEvents(CREATE)
        }
    }

    val mock =
        mockk<TestThing> {
            justRun {
                called()
            }
            justRun {
                error(any())
            }
        }

    val publisher = SomePublisherBase()
    val subscriber = object: TransEventSubscriberBase<IdentifiableEntity<Int>, CrudEvent<Int, IdentifiableEntity<Int>>>("Subscriber") {}
    val subscription = object: CrudEventSubscription<Int, IdentifiableEntity<Int>>(publisher, { mock.called() }) {}

    "Subscriber receives events from a publisher" {
        publisher.subscribe(subscriber)

        subscriber.addOnSubscribeEventAction { subscription -> subscription.source shouldBe publisher }
        shouldThrowMessage("Instances of CrudEvent cannot be requested on demand") { subscription.request(1) }

        var actualEvent: CrudEvent<Int, IdentifiableEntity<Int>>? = null
        subscriber.addOnNextEventAction(CREATE) { event -> actualEvent = event }
        subscriber.addOnNextEventAction(CREATE) { event -> actualEvent = event }

        publisher.create(1)
        eventually(100.milliseconds) {
            actualEvent should {
                it shouldNotBe null
                it!!.type shouldBe CREATE
                it.entities should { entity ->
                    entity.size shouldBe 1
                    entity[1]!!.id shouldBe 1
                }
            }
        }

        subscriber.addOnErrorEventAction {
            it.message shouldBe "Error message"
            mock.error(it)
        }
        val exception = IllegalStateException("Error message")
        subscriber.onError(exception)
        verify { mock.error(exception) }

        subscriber.addOnCompleteEventAction { mock.called() }
        publisher.finishPublishing()

        subscriber.clearSubscriptionActions()
        subscription.cancel()

        verify { mock.called() }
        confirmVerified(mock)
    }
})