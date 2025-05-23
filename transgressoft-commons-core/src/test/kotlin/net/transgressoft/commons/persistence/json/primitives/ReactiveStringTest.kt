package net.transgressoft.commons.persistence.json.primitives

import net.transgressoft.commons.event.CrudEvent
import net.transgressoft.commons.event.CrudEvent.Type.CREATE
import net.transgressoft.commons.event.CrudEvent.Type.UPDATE
import net.transgressoft.commons.event.EntityChangeEvent
import net.transgressoft.commons.event.EventType
import net.transgressoft.commons.event.TransEventSubscriberBase
import net.transgressoft.commons.event.TransEventSubscription
import net.transgressoft.commons.persistence.ReactivePrimitive
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainOnly
import io.kotest.matchers.date.shouldBeAfter
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.time.Duration.Companion.milliseconds

private class ReactiveStringSubscriber :
    TransEventSubscriberBase<ReactivePrimitive<String>, CrudEvent.Type, CrudEvent<String, ReactivePrimitive<String>>>("subscriber") {
    var subscriptionReceived: TransEventSubscription<ReactivePrimitive<String>, CrudEvent.Type, CrudEvent<String, ReactivePrimitive<String>>>? = null
    val receivedEvents = mutableMapOf<EventType, CrudEvent<String, ReactivePrimitive<String>>>()

    init {
        addOnNextEventAction(CREATE, UPDATE) { event ->
            receivedEvents[event.type] = event
        }

        addOnSubscribeEventAction { subscriptionReceived = it }
    }
}

class ReactiveStringTest : StringSpec({

    "Changes on reactive string propagate to subscribers" {
        val reactiveString = ReactiveString("1", "initialValue")
        val subscriber = ReactiveStringSubscriber()

        reactiveString.subscribe(subscriber)

        val oldClone = reactiveString.clone()
        val lastDateModified = reactiveString.lastDateModified

        reactiveString.value = "new value"

        eventually(100.milliseconds) {
            reactiveString.lastDateModified shouldBeAfter lastDateModified
            reactiveString.value shouldBe "new value"
            reactiveString shouldNotBe oldClone
            reactiveString.hashCode() shouldNotBe oldClone.hashCode()

            assertSoftly(subscriber.receivedEvents[UPDATE]) {
                it?.let {
                    this as EntityChangeEvent<String, ReactivePrimitive<String>>
                    this.entities.values.shouldContainOnly(reactiveString)
                    this.oldEntities.values.shouldContainOnly(oldClone)
                }
            }
        }

        reactiveString.value = null
        val otherReactiveString = ReactiveString("1", null)
        (reactiveString == otherReactiveString) shouldBe true
        reactiveString.hashCode() shouldBeEqual otherReactiveString.hashCode()

        reactiveString.value shouldBe null
        reactiveString.uniqueId shouldBe "1-null"
        reactiveString.toString() shouldBe "ReactiveString(id=1, value=null)"
    }
})