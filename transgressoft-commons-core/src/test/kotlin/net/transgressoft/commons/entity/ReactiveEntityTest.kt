package net.transgressoft.commons.entity

import net.transgressoft.commons.event.CrudEvent
import net.transgressoft.commons.event.EntityChangeEvent
import net.transgressoft.commons.event.FlowEventPublisher
import net.transgressoft.commons.event.ReactiveScope
import net.transgressoft.commons.event.StandardCrudEvent
import net.transgressoft.commons.event.isCreate
import net.transgressoft.commons.event.isDelete
import net.transgressoft.commons.event.isUpdate
import net.transgressoft.commons.persistence.CrudEventPublisherBase
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.util.UUID
import java.util.concurrent.Flow
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher

@OptIn(ExperimentalCoroutinesApi::class)
class ReactiveEntityTest : StringSpec({
    val testDispatcher = UnconfinedTestDispatcher()
    val testScope = CoroutineScope(testDispatcher)

    beforeSpec {
        ReactiveScope.setDefaultFlowScope(testScope)
        ReactiveScope.setDefaultIoScope(testScope)
    }

    afterSpec {
        ReactiveScope.setDefaultFlowScope(CoroutineScope(Dispatchers.Default.limitedParallelism(4) + SupervisorJob()))
        ReactiveScope.setDefaultIoScope(CoroutineScope(Dispatchers.IO.limitedParallelism(1) + SupervisorJob()))
    }

    "ReactiveEntityBase should notify subscribers when a property changes" {
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

    "ReactiveEntityBase should not notify subscribers when a property is set to the same value" {
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

    "ReactiveEntityBase should update lastDateModified when a property changes" {
        val entity = TestEntity(UUID.randomUUID().toString())
        val initialDate = entity.lastDateModified

        // Wait a bit to ensure time difference
        Thread.sleep(10)

        entity.name = "Updated Name"

        entity.lastDateModified.isAfter(initialDate) shouldBe true
    }

    "FlowEventPublisher should handle multiple subscribers" {
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

    "FlowEventPublisher should support Java Consumer subscriptions" {
        val entity = TestEntity(UUID.randomUUID().toString())
        val counter = AtomicInteger(0)

        val subscription = entity.subscribe(Consumer { counter.incrementAndGet() })

        entity.name = "Updated via Consumer"

        testDispatcher.scheduler.advanceUntilIdle()

        counter.get() shouldBe 1

        subscription.cancel()
    }

    "FlowEventPublisher should support Flow.Subscriber" {
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

    "CrudEventPublisherBase should publish CREATE events" {
        val publisher = TestCrudEventPublisher()
        val receivedEvents = mutableListOf<CrudEvent<String, TestEntity>>()

        val subscription =
            publisher.subscribe { event ->
                if (event.isCreate()) {
                    receivedEvents.add(event)
                }
            }

        val entity = TestEntity(UUID.randomUUID().toString())
        publisher.create(entity)

        testDispatcher.scheduler.advanceUntilIdle()

        receivedEvents.size shouldBe 1
        val event = receivedEvents[0]
        event.entities.shouldContainExactly(mapOf(entity.id to entity))

        subscription.cancel()
    }

    "CrudEventPublisherBase should publish UPDATE events" {
        val publisher = TestCrudEventPublisher()
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

        publisher.update(updatedEntity, originalEntity)

        testDispatcher.scheduler.advanceUntilIdle()

        receivedEvents.size shouldBe 1
        val event = receivedEvents[0]
        event.entities.shouldContainExactly(mapOf(updatedEntity.id to updatedEntity))
        (event as EntityChangeEvent<String, TestEntity>).oldEntities.shouldContainExactly(mapOf(originalEntity.id to originalEntity))

        subscription.cancel()
    }

    "CrudEventPublisherBase should publish DELETE events" {
        val publisher = TestCrudEventPublisher()
        val receivedEvents = mutableListOf<CrudEvent<String, TestEntity>>()

        val subscription =
            publisher.subscribe { event ->
                if (event.isDelete()) {
                    receivedEvents.add(event)
                }
            }

        val entity = TestEntity(UUID.randomUUID().toString())
        publisher.delete(entity)

        testDispatcher.scheduler.advanceUntilIdle()

        receivedEvents.size shouldBe 1
        val event = receivedEvents[0]
        event.entities.shouldContainExactly(mapOf(entity.id to entity))

        subscription.cancel()
    }

    "CrudEventPublisherBase should handle subscriber for specific event type" {
        val publisher = TestCrudEventPublisher()
        val createCounter = AtomicInteger(0)
        val updateCounter = AtomicInteger(0)
        val deleteCounter = AtomicInteger(0)

        val createSubscription = publisher.subscribe(StandardCrudEvent.Type.CREATE, Consumer { createCounter.incrementAndGet() })
        val updateSubscription = publisher.subscribe(StandardCrudEvent.Type.UPDATE, Consumer { updateCounter.incrementAndGet() })
        val deleteSubscription = publisher.subscribe(StandardCrudEvent.Type.DELETE, Consumer { deleteCounter.incrementAndGet() })

        val entity = TestEntity(UUID.randomUUID().toString())
        publisher.create(entity)

        val originalEntity = TestEntity(UUID.randomUUID().toString())
        val updatedEntity = originalEntity.clone()
        updatedEntity.name = "Updated Name"
        publisher.update(updatedEntity, originalEntity)

        publisher.delete(entity)

        testDispatcher.scheduler.advanceUntilIdle()

        createSubscription.cancel()
        updateSubscription.cancel()
        deleteSubscription.cancel()
    }

    "Flow extensions should collect entity change events" {
        val entity = TestEntity(UUID.randomUUID().toString())
        testScope.launch {
            val event = entity.changes.first()
            event.shouldBeInstanceOf<EntityChangeEvent<String, TestEntity>>()
            event.entities[entity.id]?.name shouldBe "Collected via Flow"
        }

        entity.name = "Collected via Flow"
        testDispatcher.scheduler.advanceUntilIdle()
    }

    "ReactiveEntityBase should properly clone before modification" {
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

class TestCrudEventPublisher : CrudEventPublisherBase<String, TestEntity>("TestPublisher") {
    fun create(entity: TestEntity) {
        putCreateEvent(entity)
    }

    fun update(entity: TestEntity, oldEntity: TestEntity) {
        putUpdateEvent(entity, oldEntity)
    }

    fun delete(entity: TestEntity) {
        putDeleteEvent(entity)
    }
}