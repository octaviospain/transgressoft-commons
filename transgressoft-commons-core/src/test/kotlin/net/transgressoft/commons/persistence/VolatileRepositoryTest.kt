package net.transgressoft.commons.persistence

import net.transgressoft.commons.Person
import net.transgressoft.commons.arbitraryPerson
import net.transgressoft.commons.entity.toIds
import net.transgressoft.commons.event.CrudEvent
import net.transgressoft.commons.event.CrudEvent.Type.CREATE
import net.transgressoft.commons.event.CrudEvent.Type.DELETE
import net.transgressoft.commons.event.CrudEvent.Type.READ
import net.transgressoft.commons.event.CrudEvent.Type.UPDATE
import net.transgressoft.commons.event.EntityChangeEvent
import net.transgressoft.commons.event.EventType
import net.transgressoft.commons.event.ReactiveScope
import net.transgressoft.commons.event.TransEventSubscriberBase
import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldContainOnly
import io.kotest.matchers.optional.shouldBePresent
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.next
import io.kotest.property.arbitrary.set
import io.kotest.property.checkAll
import java.util.Optional
import java.util.concurrent.atomic.AtomicInteger
import java.util.stream.Collectors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher

@ExperimentalCoroutinesApi
internal class VolatileRepositoryTest : StringSpec({

    class SomeClassSubscribedToEvents() : TransEventSubscriberBase<Person, CrudEvent.Type, CrudEvent<Int, Person>>("Some Name") {
        val createEventEntities = AtomicInteger(0)
        val deletedEventEntities = AtomicInteger(0)
        val receivedEvents = mutableMapOf<EventType, CrudEvent<Int, Person>>()

        init {
            addOnNextEventAction(CREATE, UPDATE) { event ->
                receivedEvents[event.type] = event
                createEventEntities.getAndUpdate { it + event.entities.size }
            }
            addOnNextEventAction(DELETE) { event ->
                receivedEvents[event.type] = event
                deletedEventEntities.getAndUpdate { it + event.entities.size }
            }
            addOnNextEventAction(READ) { receivedEvents[it.type] = it }
        }
    }

    lateinit var repository: VolatileRepository<Int, Person>
    lateinit var subscriber: SomeClassSubscribedToEvents

    val testDispatcher = UnconfinedTestDispatcher()
    val testScope = CoroutineScope(testDispatcher)

    beforeSpec {
        ReactiveScope.flowScope = testScope
        ReactiveScope.ioScope = testScope
    }

    beforeTest {
        repository =
            VolatileRepository<Int, Person>("VolatilePersonRepository").apply {
                activateEvents(READ)
            }

        subscriber = SomeClassSubscribedToEvents()
        repository.subscribe(subscriber)
    }

    afterSpec {
        ReactiveScope.resetDefaultIoScope()
        ReactiveScope.resetDefaultFlowScope()
    }

    "Repository reflects addition, replacement and deletion of entities" {
        checkAll(arbitraryPerson()) { person ->
            repository.isEmpty shouldBe true
            repository + person shouldBe true
            repository.isEmpty shouldBe false
            repository.findById(person.id) shouldBe Optional.of(person)
            repository.findByUniqueId(person.uniqueId) shouldBePresent { it shouldBe person }
            repository.search { it.money == person.money }.shouldContainOnly(person)
            repository.contains(person.id) shouldBe true
            repository.contains { it == person } shouldBe true

            val entity2 = person.copy(initialName = "Ken")
            person shouldNotBe entity2
            repository.addOrReplace(entity2) shouldBe true
            repository.findById(person.id) shouldBePresent { it shouldBe entity2 }
            repository.size() shouldBe 1

            repository.addOrReplace(entity2) shouldBe false
            repository.findFirst { it.id == person.id } shouldBePresent { it shouldBe entity2 }
            repository.size() shouldBe 1

            repository - entity2 shouldBe true
            repository.isEmpty shouldBe true

            val repository2: Repository<Int, Person> = VolatileRepository<Int, Person>("Repository2")
            repository shouldBe repository2
        }
    }

    "Repository run actions on events and send update events when they are modified" {
        val person = arbitraryPerson().next()
        repository.add(person) shouldBe true
        val previousMoney = person.money!!
        repository.runForSingle(person.id) { it.money = it.money?.plus(1) } shouldBe true
        repository.findById(person.id).get().money shouldBe previousMoney + 1

        testDispatcher.scheduler.advanceUntilIdle()

        assertSoftly(subscriber.receivedEvents[UPDATE]) {
            it?.let {
                this as EntityChangeEvent<Int, Person>
                this.entities.values.shouldContainOnly(person)
                this.oldEntities.values.shouldContainOnly(person.copy(money = previousMoney))
            }
        }

        val set = Arb.Companion.set(arbitraryPerson(), 2..2).next()
        val previousSetMoney = set.stream().collect(Collectors.toMap({ it.id }, { it.money }))
        repository + set shouldBe true
        repository.size() shouldBe set.size + 1
        repository.runForMany(set.toIds()) { it.money = it.money?.plus(1) } shouldBe true

        testDispatcher.scheduler.advanceUntilIdle()

        assertSoftly(subscriber.receivedEvents[UPDATE]) {
            it?.let {
                this as EntityChangeEvent<Int, Person>
                this.entities.values shouldContainAll set
                this.oldEntities.values.shouldContainAll(set.map { it.copy(money = previousSetMoney[it.id]) })
            }
        }

        previousSetMoney.entries.forEach { entry ->
            repository.findById(entry.key).get().money shouldBe entry.value?.plus(1)
        }

        val poorPerson = arbitraryPerson().next().copy(money = 0)
        val richPerson = arbitraryPerson().next().copy(money = 1000)
        repository.addOrReplaceAll(setOf(poorPerson, richPerson)) shouldBe true
        repository.size() shouldBe set.size + 3
        repository.runMatching({ it.money!! < 100 }) { it.money = it.money?.plus(1) } shouldBe true

        testDispatcher.scheduler.advanceUntilIdle()

        assertSoftly(subscriber.receivedEvents[UPDATE]) {
            it?.let {
                this as EntityChangeEvent<Int, Person>
                this.entities.values.shouldContainOnly(poorPerson)
                this.oldEntities.values.shouldContainOnly(poorPerson.copy(money = 0))
            }
        }
    }

    "Repository publishes CRUD events received by a subscriber" {
        val person = arbitraryPerson().next()
        val person2 = arbitraryPerson().next()
        repository.addOrReplaceAll(setOf(person, person2)) shouldBe true

        testDispatcher.scheduler.advanceUntilIdle()

        assertSoftly(subscriber.receivedEvents[CREATE]) {
            this?.isCreate() shouldBe true
            this?.entities?.values shouldContainOnly setOf(person, person2)
        }
        subscriber.createEventEntities.get() shouldBe 2
        subscriber.deletedEventEntities.get() shouldBe 0

        repository - setOf(person, person2) shouldBe true

        testDispatcher.scheduler.advanceUntilIdle()

        assertSoftly(subscriber.receivedEvents[DELETE]) {
            this?.isDelete() shouldBe true
            this?.entities?.values shouldContainOnly setOf(person, person2)
        }
        subscriber.createEventEntities.get() shouldBe 2
        subscriber.deletedEventEntities.get() shouldBe 2

        repository.add(person) shouldBe true
        repository.findById(person.id) shouldBePresent { it shouldBe person }

        testDispatcher.scheduler.advanceUntilIdle()

        assertSoftly(subscriber.receivedEvents[READ]) {
            this?.isRead() shouldBe true
            this?.entities?.values shouldContainOnly setOf(person)
        }
        subscriber.createEventEntities.get() shouldBe 3
        subscriber.deletedEventEntities.get() shouldBe 2

        val entityModified = person.copy(initialName = "Octavio")
        repository.addOrReplace(entityModified) shouldBe true

        testDispatcher.scheduler.advanceUntilIdle()

        assertSoftly(subscriber.receivedEvents[UPDATE]) {
            this?.isUpdate() shouldBe true
            this as EntityChangeEvent<Int, Person>
            this.entities.values.shouldContainOnly(entityModified)
            this.oldEntities.values.shouldContainOnly(person)
            this.oldEntities[person.id] shouldBe person
        }
        subscriber.createEventEntities.get() shouldBe 4
        subscriber.deletedEventEntities.get() shouldBe 2

        repository.clear()

        testDispatcher.scheduler.advanceUntilIdle()

        assertSoftly(subscriber.receivedEvents[DELETE]) {
            this?.isDelete() shouldBe true
            this?.entities?.values.shouldContainOnly(entityModified)
        }
        subscriber.createEventEntities.get() shouldBe 4
        subscriber.deletedEventEntities.get() shouldBe 3
    }

    "Repository disableEvents method prevents events from being published" {
        val person = arbitraryPerson().next()

        // First add with events enabled (default state)
        repository.add(person) shouldBe true

        testDispatcher.scheduler.advanceUntilIdle()

        subscriber.receivedEvents[CREATE] shouldNotBe null
        subscriber.createEventEntities.get() shouldBe 1

        // Clear received events for clean state
        subscriber.receivedEvents.clear()
        subscriber.createEventEntities.set(0)

        // Disable CREATE events
        repository.disableEvents(CREATE)

        // Add another person, but event should not be received
        val person2 = arbitraryPerson().next()
        repository.add(person2) shouldBe true

        testDispatcher.scheduler.advanceUntilIdle()

        subscriber.receivedEvents[CREATE] shouldBe null
        subscriber.createEventEntities.get() shouldBe 0

        // Re-enable events and verify they work again
        repository.activateEvents(CREATE)
        val person3 = arbitraryPerson().next()
        repository.add(person3) shouldBe true

        testDispatcher.scheduler.advanceUntilIdle()

        subscriber.receivedEvents[CREATE] shouldNotBe null
        subscriber.createEventEntities.get() shouldBe 1
    }

    "TransEventSubscriber error and complete actions are triggered correctly" {
        // Create a subscriber with error and complete handlers
        val errorFired = AtomicInteger(0)
        val completeFired = AtomicInteger(0)
        val errorMsg = mutableListOf<String>()

        val testSubscriber =
            object : TransEventSubscriberBase<Person, CrudEvent.Type, CrudEvent<Int, Person>>("ErrorCompleteSubscriber") {
                init {
                    addOnNextEventAction(CREATE) { /* Just observe */ }

                    addOnErrorEventAction { error ->
                        errorFired.incrementAndGet()
                        errorMsg.add(error.message ?: "Unknown error")
                    }

                    addOnCompleteEventAction {
                        completeFired.incrementAndGet()
                    }
                }
            }

        // Subscribe to repository
        repository.subscribe(testSubscriber)

        // Simulate error event
        val testError = RuntimeException("Test error message")
        testSubscriber.onError(testError)

        errorFired.get() shouldBe 1
        errorMsg.first() shouldBe "Test error message"

        // Simulate complete event
        testSubscriber.onComplete()

        completeFired.get() shouldBe 1

        // Test clearSubscriptionActions
        testSubscriber.clearSubscriptionActions()

        // These should not increment counters since actions were cleared
        testSubscriber.onError(RuntimeException("Another error"))
        testSubscriber.onComplete()

        errorFired.get() shouldBe 1 // Still 1, not 2
        completeFired.get() shouldBe 1 // Still 1, not 2
    }

    "Anonymous subscription test" {
        // Create counters to track events
        val createEventsReceived = AtomicInteger(0)
        val updateEventsReceived = AtomicInteger(0)
        val receivedPersonIds = mutableSetOf<Int>()

        // Create an anonymous subscription for CREATE events
        val createSubscription =
            repository.subscribe(CREATE) { event ->
                createEventsReceived.incrementAndGet()
                event.entities.keys.forEach { receivedPersonIds.add(it) }
            }

        // Create another anonymous subscription for UPDATE events
        val updateSubscription =
            repository.subscribe(UPDATE) { event ->
                updateEventsReceived.incrementAndGet()
            }

        // Add a person to trigger CREATE event
        val person = arbitraryPerson().next()
        repository.add(person) shouldBe true

        testDispatcher.scheduler.advanceUntilIdle()

        createEventsReceived.get() shouldBe 1
        receivedPersonIds shouldContainOnly setOf(person.id)
        updateEventsReceived.get() shouldBe 0

        // Update the person to trigger UPDATE event
        repository.runForSingle(person.id) { it.money = it.money?.plus(100) } shouldBe true

        testDispatcher.scheduler.advanceUntilIdle()

        createEventsReceived.get() shouldBe 1
        updateEventsReceived.get() shouldBe 1

        // Cancel the CREATE subscription
        createSubscription.cancel()

        // Add another person - should not trigger the canceled subscription
        val person2 = arbitraryPerson().next()
        repository.add(person2) shouldBe true

        testDispatcher.scheduler.advanceUntilIdle()

        // CREATE counter should not have increased since we canceled the subscription
        createEventsReceived.get() shouldBe 1
        receivedPersonIds shouldContainOnly setOf(person.id) // Should not contain person2.id

        // But UPDATE subscription should still work
        repository.runForSingle(person2.id) { it.money = it.money?.plus(100) } shouldBe true

        testDispatcher.scheduler.advanceUntilIdle()

        updateEventsReceived.get() shouldBe 2

        // Cancel the UPDATE subscription too
        updateSubscription.cancel()
    }
})