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
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.ExperimentalUuidApi

private class ReactiveBooleanSubscriber :
    TransEventSubscriberBase<ReactivePrimitive<Boolean>, CrudEvent.Type, CrudEvent<String, ReactivePrimitive<Boolean>>>("subscriber") {
    var subscriptionReceived: TransEventSubscription<ReactivePrimitive<Boolean>, CrudEvent.Type, CrudEvent<String, ReactivePrimitive<Boolean>>>? = null
    val receivedEvents = mutableMapOf<EventType, CrudEvent<String, ReactivePrimitive<Boolean>>>()

    init {
        addOnNextEventAction(CREATE, UPDATE) { event ->
            receivedEvents[event.type] = event
        }

        addOnSubscribeEventAction { subscriptionReceived = it }
    }
}

@OptIn(ExperimentalUuidApi::class)
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
            reactiveBoolean shouldNotBe oldClone
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
        reactiveBoolean shouldBe otherReactiveBoolean
        reactiveBoolean.hashCode() shouldBeEqual otherReactiveBoolean.hashCode()

        reactiveBoolean.value shouldBe null
        reactiveBoolean.uniqueId shouldBe "1-null"
        reactiveBoolean.toString() shouldBe "ReactiveBoolean(id=1, value=null)"

        val reactiveBooleanWithRandomULID = ReactiveBoolean(true)
        reactiveBooleanWithRandomULID.id.shouldNotBeNull()
    }
})