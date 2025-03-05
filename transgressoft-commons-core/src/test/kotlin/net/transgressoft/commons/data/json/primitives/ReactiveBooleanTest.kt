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

private class ReactiveBooleanSubscriber :
    TransEventSubscriberBase<ReactivePrimitive<Boolean>, CrudEvent<String, ReactivePrimitive<Boolean>>>("subscriber") {
    var subscriptionReceived: TransEventSubscription<ReactivePrimitive<Boolean>>? = null
    val receivedEvents = mutableMapOf<EventType, CrudEvent<String, ReactivePrimitive<Boolean>>>()

    init {
        addOnNextEventAction(CREATE, UPDATE) { event ->
            receivedEvents[event.type] = event
        }

        addOnSubscribeEventAction { subscriptionReceived = it }
    }
}

class ReactiveBooleanTest : StringSpec({

    "Changes on reactive boolean propagate to subscribers" {
        val reactiveBoolean = ReactiveBoolean("1", true)
        val subscriber = ReactiveBooleanSubscriber()

        reactiveBoolean.subscribe(subscriber)

        val oldClone = reactiveBoolean.clone()
        val lastDateModified = reactiveBoolean.lastDateModified

        reactiveBoolean.value = false

        eventually(100.milliseconds) {
            reactiveBoolean.lastDateModified shouldBeAfter lastDateModified
            reactiveBoolean.value shouldBe false
            reactiveBoolean.equals(oldClone) shouldBe false
            reactiveBoolean.hashCode() shouldNotBe oldClone.hashCode()

            assertSoftly(subscriber.receivedEvents[UPDATE]) {
                it?.let {
                    this as EntityChangeEvent<String, ReactivePrimitive<Boolean>>
                    this.entities.values.shouldContainOnly(reactiveBoolean)
                    this.oldEntities.values.shouldContainOnly(oldClone)
                }
            }
        }

        reactiveBoolean.value = null
        val otherReactiveBoolean = ReactiveBoolean("1", null)
        reactiveBoolean.equals(otherReactiveBoolean) shouldBe true
        reactiveBoolean.hashCode() shouldBeEqual otherReactiveBoolean.hashCode()

        eventually(100.milliseconds) {
            reactiveBoolean.value shouldBe null
            reactiveBoolean.uniqueId shouldBe "1-null"
            reactiveBoolean.toString() shouldBe "ReactiveBoolean(id=1, value=null)"
        }
    }
})