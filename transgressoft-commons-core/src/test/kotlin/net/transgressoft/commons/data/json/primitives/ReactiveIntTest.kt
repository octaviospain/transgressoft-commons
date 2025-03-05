package net.transgressoft.commons.data.json.primitives

import net.transgressoft.commons.EntityChangeEvent
import net.transgressoft.commons.EventType
import net.transgressoft.commons.TransEventSubscriberBase
import net.transgressoft.commons.TransEventSubscription
import net.transgressoft.commons.data.CrudEvent
import net.transgressoft.commons.data.ReactivePrimitive
import net.transgressoft.commons.data.StandardCrudEvent.Type.CREATE
import net.transgressoft.commons.data.StandardCrudEvent.Type.UPDATE
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainOnly
import io.kotest.matchers.date.shouldBeAfter
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.time.Duration.Companion.milliseconds

private class ReactiveIntSubscriber :
    TransEventSubscriberBase<ReactivePrimitive<Int>, CrudEvent<String, ReactivePrimitive<Int>>>("subscriber") {
    var subscriptionReceived: TransEventSubscription<ReactivePrimitive<Int>>? = null
    val receivedEvents = mutableMapOf<EventType, CrudEvent<String, ReactivePrimitive<Int>>>()

    init {
        addOnNextEventAction(CREATE, UPDATE) { event ->
            receivedEvents[event.type] = event
        }

        addOnSubscribeEventAction { subscriptionReceived = it }
    }
}

class ReactiveIntTest : StringSpec({

    "Changes on reactive int propagate to subscribers" {
        val reactiveInt = ReactiveInt("id1", 1)
        val subscriber = ReactiveIntSubscriber()

        reactiveInt.subscribe(subscriber)

        val oldClone = reactiveInt.clone()
        val lastDateModified = reactiveInt.lastDateModified

        reactiveInt.value = 2

        eventually(100.milliseconds) {
            reactiveInt.lastDateModified shouldBeAfter lastDateModified
            reactiveInt.value shouldBe 2
            reactiveInt.equals(oldClone) shouldBe false
            reactiveInt.hashCode() shouldNotBe oldClone.hashCode()

            assertSoftly(subscriber.receivedEvents[UPDATE]) {
                it?.let {
                    this as EntityChangeEvent<String, ReactivePrimitive<Int>>
                    this.entities.values.shouldContainOnly(reactiveInt)
                    this.oldEntities.values.shouldContainOnly(oldClone)
                }
            }
        }

        reactiveInt.value = null
        val otherReactiveInt = ReactiveInt("id1", null)
        reactiveInt.equals(otherReactiveInt) shouldBe true
        reactiveInt.hashCode() shouldBeEqual otherReactiveInt.hashCode()

        eventually(100.milliseconds) {
            reactiveInt.value shouldBe null
            reactiveInt.uniqueId shouldBe "id1-null"
            reactiveInt.toString() shouldBe "ReactiveInt(id=id1, value=null)"
        }
    }
})