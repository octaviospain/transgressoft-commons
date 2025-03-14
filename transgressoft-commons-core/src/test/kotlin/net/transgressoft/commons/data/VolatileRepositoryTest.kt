package net.transgressoft.commons.data

import net.transgressoft.commons.EntityChangeEvent
import net.transgressoft.commons.EventType
import net.transgressoft.commons.TransEventSubscriberBase
import net.transgressoft.commons.data.StandardCrudEvent.Type.CREATE
import net.transgressoft.commons.data.StandardCrudEvent.Type.DELETE
import net.transgressoft.commons.data.StandardCrudEvent.Type.READ
import net.transgressoft.commons.data.StandardCrudEvent.Type.UPDATE
import net.transgressoft.commons.toIds
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.nondeterministic.eventually
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
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.stream.Collectors
import kotlin.time.Duration.Companion.milliseconds

internal class VolatileRepositoryTest : StringSpec({

    class SomeClassSubscribedToEvents() : TransEventSubscriberBase<Person, CrudEvent<Int, Person>>("Some class") {
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

    class VolatilePersonRepository : VolatileRepository<Int, Person>("VolatilePersonRepository") {
        init {
            activateEvents(CREATE, READ, UPDATE, DELETE)
        }
    }

    lateinit var repository: VolatilePersonRepository
    lateinit var subscriber: SomeClassSubscribedToEvents

    beforeTest {
        repository = VolatilePersonRepository()
        subscriber = SomeClassSubscribedToEvents()
        repository.subscribe(subscriber)
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

            val repository2: Repository<Int, Person> = VolatilePersonRepository()
            repository shouldBe repository2
        }
    }

    "Repository run actions on events and send update events when they are modified" {
        val person = arbitraryPerson().next()
        repository.add(person) shouldBe true
        val previousMoney = person.money!!
        repository.runForSingle(person.id) { it.money = it.money?.plus(1) } shouldBe true
        repository.findById(person.id).get().money shouldBe previousMoney + 1

        eventually(100.milliseconds) {
            assertSoftly(subscriber.receivedEvents[UPDATE]) {
                it?.let {
                    this as EntityChangeEvent<Int, Person>
                    this.entities.values.shouldContainOnly(person)
                    this.oldEntities.values.shouldContainOnly(person.copy(money = previousMoney))
                }
            }
        }

        val set = Arb.set(arbitraryPerson(), 2..2).next()
        val previousSetMoney = set.stream().collect(Collectors.toMap({ it.id }, { it.money }))
        repository + set shouldBe true
        repository.size() shouldBe set.size + 1
        repository.runForMany(set.toIds()) { it.money = it.money?.plus(1) } shouldBe true

        eventually(100.milliseconds) {
            assertSoftly(subscriber.receivedEvents[UPDATE]) {
                it?.let {
                    this as EntityChangeEvent<Int, Person>
                    this.entities.values shouldContainAll set
                    this.oldEntities.values.shouldContainAll(set.map { it.copy(money = previousSetMoney[it.id]) })
                }
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

        eventually(100.milliseconds) {
            assertSoftly(subscriber.receivedEvents[UPDATE]) {
                it?.let {
                    this as EntityChangeEvent<Int, Person>
                    this.entities.values.shouldContainOnly(poorPerson)
                    this.oldEntities.values.shouldContainOnly(poorPerson.copy(money = 0))
                }
            }
        }
    }

    "Repository publishes CRUD events received by a subscriber" {
        val person = arbitraryPerson().next()
        val person2 = arbitraryPerson().next()
        repository.addOrReplaceAll(setOf(person, person2)) shouldBe true
        eventually(100.milliseconds) {
            assertSoftly(subscriber.receivedEvents[CREATE]) {
                this?.isCreate() shouldBe true
                this?.entities?.values shouldContainOnly setOf(person, person2)
            }
            subscriber.createEventEntities.get() shouldBe 2
            subscriber.deletedEventEntities.get() shouldBe 0
        }

        repository - setOf(person, person2) shouldBe true
        eventually(100.milliseconds) {
            assertSoftly(subscriber.receivedEvents[DELETE]) {
                this?.isDelete() shouldBe true
                this?.entities?.values shouldContainOnly setOf(person, person2)
            }
            subscriber.createEventEntities.get() shouldBe 2
            subscriber.deletedEventEntities.get() shouldBe 2
        }

        repository.add(person) shouldBe true
        repository.findById(person.id) shouldBePresent { it shouldBe person }
        eventually(100.milliseconds) {
            assertSoftly(subscriber.receivedEvents[READ]) {
                this?.isRead() shouldBe true
                this?.entities?.values shouldContainOnly setOf(person)
            }
            subscriber.createEventEntities.get() shouldBe 3
            subscriber.deletedEventEntities.get() shouldBe 2
        }

        val entityModified = person.copy(initialName = "Octavio")
        repository.addOrReplace(entityModified) shouldBe true
        eventually(100.milliseconds) {
            assertSoftly(subscriber.receivedEvents[UPDATE]) {
                this?.isUpdate() shouldBe true
                this as EntityChangeEvent<Int, Person>
                this.entities.values.shouldContainOnly(entityModified)
                this.oldEntities.values.shouldContainOnly(person)
                this.oldEntities[person.id] shouldBe person
            }
            subscriber.createEventEntities.get() shouldBe 4
            subscriber.deletedEventEntities.get() shouldBe 2
        }

        repository.clear()
        eventually(100.milliseconds) {
            assertSoftly(subscriber.receivedEvents[DELETE]) {
                this?.isDelete() shouldBe true
                this?.entities?.values.shouldContainOnly(entityModified)
            }
            subscriber.createEventEntities.get() shouldBe 4
            subscriber.deletedEventEntities.get() shouldBe 3
        }
    }
})