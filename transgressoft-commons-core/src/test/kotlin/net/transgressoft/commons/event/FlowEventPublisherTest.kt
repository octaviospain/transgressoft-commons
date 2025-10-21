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

package net.transgressoft.commons.event

import net.transgressoft.commons.entity.ReactiveEntityBase
import net.transgressoft.commons.event.CrudEvent.Type.CREATE
import net.transgressoft.commons.event.CrudEvent.Type.DELETE
import net.transgressoft.commons.event.CrudEvent.Type.UPDATE
import net.transgressoft.commons.event.StandardCrudEvent.Create
import net.transgressoft.commons.event.StandardCrudEvent.Delete
import net.transgressoft.commons.event.StandardCrudEvent.Update
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.util.UUID
import java.util.concurrent.Flow
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher

@ExperimentalCoroutinesApi
class FlowEventPublisherTest : StringSpec({
    val testDispatcher = UnconfinedTestDispatcher()
    val testScope = CoroutineScope(testDispatcher)

    beforeSpec {
        ReactiveScope.flowScope = testScope
        ReactiveScope.ioScope = testScope
    }

    afterSpec {
        ReactiveScope.resetDefaultIoScope()
        ReactiveScope.resetDefaultFlowScope()
    }

    "ReactiveEntity emits change event on property modification" {
        val entity = TestEntity(UUID.randomUUID().toString())
        val receivedEvents = mutableListOf<EntityChangeEvent<String, TestEntity>>()

        val subscription =
            entity.subscribe { event ->
                receivedEvents.add(event)
            }

        val oldName = entity.name
        val newName = "Updated Name"
        entity.name = newName

        testDispatcher.scheduler.advanceUntilIdle()

        receivedEvents.size shouldBe 1
        val event = receivedEvents[0]
        event.shouldBeInstanceOf<EntityChangeEvent<String, TestEntity>>()
        event.entities[entity.id]?.name shouldBe newName
        event.oldEntities[entity.id]?.name shouldBe oldName

        subscription.cancel()
    }

    "ReactiveEntity does not emit change event when property is set to its current value" {
        val entity = TestEntity(UUID.randomUUID().toString())
        val receivedEvents = mutableListOf<EntityChangeEvent<String, TestEntity>>()

        val subscription =
            entity.subscribe { event ->
                receivedEvents.add(event)
            }

        val oldName = entity.name
        entity.name = oldName // Same value

        testDispatcher.scheduler.advanceUntilIdle()

        receivedEvents.size shouldBe 0

        subscription.cancel()
    }

    "ReactiveEntity updates lastDateModified on property modification" {
        val entity = TestEntity(UUID.randomUUID().toString())
        val initialDate = entity.lastDateModified

        // Wait a bit to ensure time difference
        Thread.sleep(10)

        entity.name = "Updated Name"

        entity.lastDateModified.isAfter(initialDate) shouldBe true
    }

    "FlowEventPublisher distributes events to multiple subscribers" {
        val entity = TestEntity(UUID.randomUUID().toString())
        val counter1 = AtomicInteger(0)
        val counter2 = AtomicInteger(0)

        val subscription1 = entity.subscribe { counter1.incrementAndGet() }
        val subscription2 = entity.subscribe { counter2.incrementAndGet() }

        entity.name = "First update"
        entity.name = "Second update"

        testDispatcher.scheduler.advanceUntilIdle()

        counter1.get() shouldBe 2
        counter2.get() shouldBe 2

        subscription1.cancel()
        entity.name = "Third update"

        testDispatcher.scheduler.advanceUntilIdle()

        counter1.get() shouldBe 2 // Unchanged after cancellation
        counter2.get() shouldBe 3

        subscription2.cancel()
    }

    "FlowEventPublisher supports Java Consumer subscriptions" {
        val entity = TestEntity(UUID.randomUUID().toString())
        val counter = AtomicInteger(0)

        val subscription = entity.subscribe(Consumer { counter.incrementAndGet() })

        entity.name = "Updated via Consumer"

        testDispatcher.scheduler.advanceUntilIdle()

        counter.get() shouldBe 1

        subscription.cancel()
    }

    "FlowEventPublisher supports Java Flow.Subscriber subscriptions" {
        val entity = TestEntity(UUID.randomUUID().toString())
        val counter = AtomicInteger(0)

        val subscriber =
            object : Flow.Subscriber<EntityChangeEvent<String, TestEntity>> {
                override fun onSubscribe(subscription: Flow.Subscription) {
                    // In a real subscriber, you'd maybe call subscription.request(Long.MAX_VALUE) here, although in this library doesn't make sense
                }

                override fun onNext(item: EntityChangeEvent<String, TestEntity>) {
                    counter.incrementAndGet()
                }

                override fun onError(throwable: Throwable) {}

                override fun onComplete() {}
            }

        entity.subscribe(subscriber)

        entity.name = "Updated via Flow.Subscriber"

        testDispatcher.scheduler.advanceUntilIdle()

        counter.get() shouldBe 1
    }

    "FlowEventPublisher publishes CREATE events" {
        val publisher =
            FlowEventPublisher<CrudEvent.Type, CrudEvent<String, TestEntity>>("Publisher").apply {
                activateEvents(CREATE)
            }
        val receivedEvents = mutableListOf<CrudEvent<String, TestEntity>>()

        val subscription =
            publisher.subscribe { event ->
                if (event.isCreate()) {
                    receivedEvents.add(event)
                }
            }

        val entity = TestEntity(UUID.randomUUID().toString())
        publisher.emitAsync(Create(entity))

        testDispatcher.scheduler.advanceUntilIdle()

        receivedEvents.size shouldBe 1
        val event = receivedEvents[0]
        event.entities.shouldContainExactly(mapOf(entity.id to entity))

        subscription.cancel()
    }

    "FlowEventPublisher publishes UPDATE events" {
        val publisher =
            FlowEventPublisher<CrudEvent.Type, CrudEvent<String, TestEntity>>("Publisher").apply {
                activateEvents(UPDATE)
            }
        val receivedEvents = mutableListOf<CrudEvent<String, TestEntity>>()

        val subscription =
            publisher.subscribe { event ->
                if (event.isUpdate()) {
                    receivedEvents.add(event)
                }
            }

        val originalEntity = TestEntity(UUID.randomUUID().toString())
        val updatedEntity = originalEntity.clone()
        updatedEntity.name = "Updated Name"

        publisher.emitAsync(Update(updatedEntity, originalEntity))

        testDispatcher.scheduler.advanceUntilIdle()

        receivedEvents.size shouldBe 1
        val event = receivedEvents[0]
        event.entities.shouldContainExactly(mapOf(updatedEntity.id to updatedEntity))
        (event as EntityChangeEvent<String, TestEntity>).oldEntities.shouldContainExactly(mapOf(originalEntity.id to originalEntity))

        subscription.cancel()
    }

    "FlowEventPublisher publishes DELETE events" {
        val publisher =
            FlowEventPublisher<CrudEvent.Type, CrudEvent<String, TestEntity>>("Publisher").apply {
                activateEvents(DELETE)
            }
        val receivedEvents = mutableListOf<CrudEvent<String, TestEntity>>()

        val subscription =
            publisher.subscribe { event ->
                if (event.isDelete()) {
                    receivedEvents.add(event)
                }
            }

        val entity = TestEntity(UUID.randomUUID().toString())
        publisher.emitAsync(Delete(entity))

        testDispatcher.scheduler.advanceUntilIdle()

        receivedEvents.size shouldBe 1
        val event = receivedEvents[0]
        event.entities.shouldContainExactly(mapOf(entity.id to entity))

        subscription.cancel()
    }

    "FlowEventPublisher dispatches events to type-specific subscribers" {
        val publisher = FlowEventPublisher<CrudEvent.Type, CrudEvent<String, TestEntity>>("Publisher")
        val createCounter = AtomicInteger(0)
        val updateCounter = AtomicInteger(0)
        val deleteCounter = AtomicInteger(0)

        val createSubscription = publisher.subscribe(CREATE) { createCounter.incrementAndGet() }
        val updateSubscription = publisher.subscribe(UPDATE) { updateCounter.incrementAndGet() }
        val deleteSubscription = publisher.subscribe(DELETE) { deleteCounter.incrementAndGet() }

        val entity = TestEntity(UUID.randomUUID().toString())
        publisher.emitAsync(Create(entity))

        val originalEntity = TestEntity(UUID.randomUUID().toString())
        val updatedEntity = originalEntity.clone()
        updatedEntity.name = "Updated Name"
        publisher.emitAsync(Update(updatedEntity, originalEntity))

        publisher.emitAsync(Delete(entity))

        testDispatcher.scheduler.advanceUntilIdle()

        createSubscription.cancel()
        updateSubscription.cancel()
        deleteSubscription.cancel()
    }

    "ReactiveEntity changes Flow can be collected by Kotlin Flow operators" {
        val entity = TestEntity(UUID.randomUUID().toString())
        testScope.launch {
            val event = entity.changes.first()
            event.shouldBeInstanceOf<EntityChangeEvent<String, TestEntity>>()
            event.entities[entity.id]?.name shouldBe "Collected via Flow"
        }

        entity.name = "Collected via Flow"
        testDispatcher.scheduler.advanceUntilIdle()
    }

    "ReactiveEntity change event includes a deep clone of the old entity" {
        val entity = TestEntity(UUID.randomUUID().toString())
        val receivedEvents = mutableListOf<EntityChangeEvent<String, TestEntity>>()

        val subscription =
            entity.subscribe { event ->
                receivedEvents.add(event)
            }

        val originalName = entity.name
        entity.name = "New Name"

        testDispatcher.scheduler.advanceUntilIdle()

        receivedEvents.size shouldBe 1
        val event = receivedEvents[0]

        // Verify the old entity is a proper clone
        val oldEntity = event.oldEntities[entity.id]!!
        oldEntity.name shouldBe originalName
        oldEntity.id shouldBe entity.id

        // Verify it's a different instance
        (oldEntity !== entity) shouldBe true

        subscription.cancel()
    }

    "Events are processed in order despite rapid emission" {
        val publisher = FlowEventPublisher<CrudEvent.Type, CrudEvent<String, TestEntity>>("TestPublisher")
        publisher.activateEvents(CREATE, UPDATE, DELETE)

        val receivedEvents = mutableListOf<CrudEvent<String, TestEntity>>()
        val subscription =
            publisher.subscribe { event ->
                receivedEvents.add(event)
            }

        // Emit events rapidly
        repeat(100) { i ->
            val entity = TestEntity("entity-$i")
            publisher.emitAsync(Create(entity))
        }

        testDispatcher.scheduler.advanceUntilIdle()

        receivedEvents.size shouldBe 100
        receivedEvents.forEachIndexed { index, event ->
            event.entities.values.first().id shouldBe "entity-$index"
        }

        subscription.cancel()
    }

    "Multiple subscribers all receive all events in order" {
        val publisher = FlowEventPublisher<CrudEvent.Type, CrudEvent<String, TestEntity>>("TestPublisher")
        publisher.activateEvents(CREATE)

        val subscriber1Events = mutableListOf<CrudEvent<String, TestEntity>>()
        val subscriber2Events = mutableListOf<CrudEvent<String, TestEntity>>()
        val subscriber3Events = mutableListOf<CrudEvent<String, TestEntity>>()

        val sub1 = publisher.subscribe { subscriber1Events.add(it) }
        val sub2 = publisher.subscribe { subscriber2Events.add(it) }
        val sub3 = publisher.subscribe { subscriber3Events.add(it) }

        repeat(50) { i ->
            publisher.emitAsync(Create(TestEntity("entity-$i")))
        }

        testDispatcher.scheduler.advanceUntilIdle()

        subscriber1Events.size shouldBe 50
        subscriber2Events.size shouldBe 50
        subscriber3Events.size shouldBe 50

        // All subscribers received same events in same order
        subscriber1Events.map { it.entities.values.first().id } shouldBe subscriber2Events.map { it.entities.values.first().id }
        subscriber2Events.map { it.entities.values.first().id } shouldBe subscriber3Events.map { it.entities.values.first().id }

        sub1.cancel()
        sub2.cancel()
        sub3.cancel()
    }

    "Subscriber can safely emit new events during event handling" {
        val publisher = FlowEventPublisher<CrudEvent.Type, CrudEvent<String, TestEntity>>("TestPublisher")
        publisher.activateEvents(CREATE, UPDATE)

        val allEvents = mutableListOf<CrudEvent<String, TestEntity>>()
        val processedCount = AtomicInteger(0)

        val subscription =
            publisher.subscribe { event ->
                allEvents.add(event)
                processedCount.incrementAndGet()

                // Emit a follow-up event during processing (simulating cascading updates)
                if (event.isCreate()) {
                    val entity = event.entities.values.first()
                    val updatedEntity = entity.clone()
                    publisher.emitAsync(StandardCrudEvent.Update(updatedEntity, entity))
                }
            }

        publisher.emitAsync(Create(TestEntity("entity-1")))

        testDispatcher.scheduler.advanceUntilIdle()

        // Should have processed: 1 CREATE + 1 UPDATE
        processedCount.get() shouldBe 2
        allEvents.size shouldBe 2
        allEvents[0].isCreate() shouldBe true
        allEvents[1].isUpdate() shouldBe true

        subscription.cancel()
    }

    "Channel backpressure handles burst of events without loss" {
        val publisher = FlowEventPublisher<CrudEvent.Type, CrudEvent<String, TestEntity>>("TestPublisher")
        publisher.activateEvents(CREATE)

        val receivedEvents = mutableListOf<CrudEvent<String, TestEntity>>()

        val subscription =
            publisher.subscribe { event ->
                receivedEvents.add(event)
                // Simulate slow subscriber
                delay(1.milliseconds)
            }

        // Burst emit 1000 events rapidly
        repeat(1000) { i ->
            publisher.emitAsync(Create(TestEntity("entity-$i")))
        }

        testDispatcher.scheduler.advanceUntilIdle()

        // All events should be received despite slow processing
        receivedEvents.size shouldBe 1000
        receivedEvents.forEachIndexed { index, event ->
            event.entities.values.first().id shouldBe "entity-$index"
        }

        subscription.cancel()
    }

    "Late subscriber receives no historical events (no replay)" {
        val publisher = FlowEventPublisher<CrudEvent.Type, CrudEvent<String, TestEntity>>("TestPublisher")
        publisher.activateEvents(CREATE)

        // Emit events before subscription
        repeat(10) { i ->
            publisher.emitAsync(Create(TestEntity("early-$i")))
        }

        testDispatcher.scheduler.advanceUntilIdle()

        val lateSubscriberEvents = mutableListOf<CrudEvent<String, TestEntity>>()
        val lateSubscription = publisher.subscribe { lateSubscriberEvents.add(it) }

        testDispatcher.scheduler.advanceUntilIdle()

        // Late subscriber should not receive earlier events
        lateSubscriberEvents.size shouldBe 0

        // But should receive new events
        publisher.emitAsync(Create(TestEntity("new-1")))
        testDispatcher.scheduler.advanceUntilIdle()

        lateSubscriberEvents.size shouldBe 1
        lateSubscriberEvents[0].entities.values.first().id shouldBe "new-1"

        lateSubscription.cancel()
    }

    "Cancelled subscription stops receiving events immediately" {
        val publisher = FlowEventPublisher<CrudEvent.Type, CrudEvent<String, TestEntity>>("TestPublisher")
        publisher.activateEvents(CREATE)

        val receivedEvents = mutableListOf<CrudEvent<String, TestEntity>>()
        val subscription = publisher.subscribe { receivedEvents.add(it) }

        publisher.emitAsync(Create(TestEntity("before-cancel")))
        testDispatcher.scheduler.advanceUntilIdle()
        receivedEvents.size shouldBe 1

        subscription.cancel()

        publisher.emitAsync(Create(TestEntity("after-cancel")))
        testDispatcher.scheduler.advanceUntilIdle()

        // Should not have received event after cancellation
        receivedEvents.size shouldBe 1
    }
})

class TestEntity(override val id: String) : ReactiveEntityBase<String, TestEntity>(FlowEventPublisher(id)) {
    var name: String = "Initial Name"
        set(value) {
            setAndNotify(value, field) { field = it }
        }

    override val uniqueId = "$id-$name"

    var description: String = "Initial Description"
        set(value) {
            setAndNotify(value, field) { field = it }
        }

    override fun clone(): TestEntity {
        val clone = TestEntity(id)
        clone.name = this.name
        clone.description = this.description
        return clone
    }

    override fun toString(): String = "TestEntity(id=$id, name=$name, description=$description)"
}