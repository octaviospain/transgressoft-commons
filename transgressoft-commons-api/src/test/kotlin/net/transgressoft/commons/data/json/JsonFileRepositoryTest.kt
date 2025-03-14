package net.transgressoft.commons.data.json

import net.transgressoft.commons.ReactiveScope
import net.transgressoft.commons.TransEventSubscription
import net.transgressoft.commons.data.CrudEvent
import net.transgressoft.commons.data.Man
import net.transgressoft.commons.data.ManGenericJsonFileRepository
import net.transgressoft.commons.data.Person
import net.transgressoft.commons.data.PersonJsonFileRepository
import net.transgressoft.commons.data.Personly
import net.transgressoft.commons.data.StandardCrudEvent.Type.CREATE
import net.transgressoft.commons.data.arbitraryPerson
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.assertions.json.shouldNotEqualJson
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempfile
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.optional.shouldBePresent
import io.kotest.matchers.shouldBe
import io.kotest.property.arbitrary.next
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import java.io.File
import java.io.IOException
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher

@OptIn(ExperimentalCoroutinesApi::class)
class JsonFileRepositoryTest: StringSpec({

    val testDispatcher = UnconfinedTestDispatcher()
    val testScope = CoroutineScope(testDispatcher)
    lateinit var jsonFile: File
    lateinit var repository: PersonJsonFileRepository

    beforeSpec {
        ReactiveScope.setDefaultFlowScope(testScope)
        ReactiveScope.setDefaultIoScope(testScope)
    }

    beforeEach {
        jsonFile = tempfile("json-repository-test", ".json").also { it.deleteOnExit() }
        repository = PersonJsonFileRepository(jsonFile)
    }

    afterEach {
        repository.close()
    }

    afterSpec {
        ReactiveScope.setDefaultFlowScope(CoroutineScope(Dispatchers.Default.limitedParallelism(4) + SupervisorJob()))
        ReactiveScope.setDefaultIoScope(CoroutineScope(Dispatchers.IO.limitedParallelism(1) + SupervisorJob()))
    }

    "Repository serializes itself to file when element is added" {
        val person = arbitraryPerson().next()
        repository.add(person) shouldBe true

        testDispatcher.scheduler.advanceUntilIdle()

        val expectedRepositoryJson = """
        {
            "${person.id}": {
                "type": "Person",
                "id": ${person.id},
                "name": "${person.name}",
                "money": ${person.money},
                "morals": ${person.morals}
            }
        }"""

        jsonFile.readText().shouldEqualJson(expectedRepositoryJson)
        PersonJsonFileRepository(jsonFile) shouldBe repository
    }

    "Repository serializes itself to file when element is added or replaced" {
        val person = arbitraryPerson().next()
        repository.add(person) shouldBe true

        val person2 = person.copy(initialName = "Ken")
        repository.addOrReplace(person2) shouldBe true

        testDispatcher.scheduler.advanceUntilIdle()
        val expectedRepositoryJson = """
        {
            "${person.id}": {
                "type": "Person",
                "id": ${person.id},
                "name": "Ken",
                "money": ${person.money},
                "morals": ${person.morals} 
            }
        }"""
        repository.addOrReplace(person2) shouldBe false
        testDispatcher.scheduler.advanceUntilIdle()
        jsonFile.readText().shouldEqualJson(expectedRepositoryJson)

        val secondRepository = PersonJsonFileRepository(file = jsonFile) shouldBe repository
        secondRepository.close()
    }

    "Person repository is initialized from existing json data and persist new elements into it" {
        val person = arbitraryPerson().next()
        jsonFile.writeText(
            """
            {
                "${person.id}": {
                    "type": "Person",
                    "id": ${person.id},
                    "name": "${person.name}",
                    "money": ${person.money},
                    "morals": ${person.morals}
                }
            }"""
        )

        repository = PersonJsonFileRepository(jsonFile)
        repository.size() shouldBe 1

        testDispatcher.scheduler.advanceUntilIdle()
        repository.findById(person.id) shouldBePresent { it shouldBe person }

        val person2 = arbitraryPerson().next()
        repository.addOrReplaceAll(setOf(person2))

        testDispatcher.scheduler.advanceUntilIdle()

        val expectedRepositoryJson = """
            {
                "${person.id}": {
                    "type": "Person",
                    "id": ${person.id},
                    "name": "${person.name}",
                    "money": ${person.money},
                    "morals": ${person.morals}
                },
                "${person2.id}": {
                    "type": "Person",
                    "id": ${person2.id},
                    "name": "${person2.name}",
                    "money": ${person2.money},
                    "morals": ${person2.morals}
                }
            }"""
        jsonFile.readText().shouldEqualJson(expectedRepositoryJson)
    }

    "Repository cannot be created from a wrong file" {
        shouldThrow<IllegalArgumentException> {
            PersonJsonFileRepository(File("/does-not-exist.txt"))
        }.message shouldBe "Provided jsonFile does not exist, is not writable or is not a json file"
    }

    "Repository can change the file where it is serialized dynamically" {
        val person = arbitraryPerson().next()
        jsonFile.writeText(
            """
            {
                "${person.id}": {
                    "type": "Person",
                    "id": ${person.id},
                    "name": "${person.name}",
                    "money": ${person.money},
                    "morals": ${person.morals}
                }
            }"""
        )

        repository.add(person)
        repository.size() shouldBe 1

        val newJsonFile = tempfile("json-repository-test", ".json").also { it.deleteOnExit() }
        repository.jsonFile = newJsonFile

        val person2 = arbitraryPerson().next()
        repository.addOrReplaceAll(setOf(person2))

        testDispatcher.scheduler.advanceUntilIdle()

        val expectedRepositoryJson = """
        {
            "${person.id}": {
                "type": "Person",
                "id": ${person.id},
                "name": "${person.name}",
                "money": ${person.money},
                "morals": ${person.morals}
            },
            "${person2.id}": {
                "type": "Person",
                "id": ${person2.id},
                "name": "${person2.name}",
                "money": ${person2.money},
                "morals": ${person2.morals}
            }
        }"""
        expectedRepositoryJson.shouldNotEqualJson(jsonFile.readText())
        expectedRepositoryJson.shouldEqualJson(newJsonFile.readText())
    }

    "Repository serializes itself when entity is modified" {
        val person = arbitraryPerson().next()
        repository.add(person)
        testDispatcher.scheduler.advanceUntilIdle()
        var expectedRepositoryJson = """
        {
            "${person.id}": {
                "type": "Person",
                "id": ${person.id},
                "name": "${person.name}",
                "money": ${person.money},
                "morals": ${person.morals}
            }
        }"""
        jsonFile.readText().shouldEqualJson(expectedRepositoryJson)
        person.name = "John Namechanged"
        testDispatcher.scheduler.advanceUntilIdle()

        expectedRepositoryJson = """
        {
            "${person.id}": {
                "type": "Person",
                "id": ${person.id},
                "name": "John Namechanged",
                "money": ${person.money},
                "morals": ${person.morals}
            }
        }"""

        jsonFile.readText().shouldEqualJson(expectedRepositoryJson)
    }

    "Repository serializes itself when entity is modified during an action on the repository" {
        val person = arbitraryPerson().next()
        repository.add(person) shouldBe true
        testDispatcher.scheduler.advanceUntilIdle()
        var expectedRepositoryJson = """
            {
                "${person.id}": {
                    "type": "Person",
                    "id": ${person.id},
                    "name": "${person.name}",
                    "money": ${person.money},
                    "morals": ${person.morals}
                }
            }"""
        jsonFile.readText().shouldEqualJson(expectedRepositoryJson)

        repository.runForSingle(person.id) { it.name = "John Namechanged" }
        testDispatcher.scheduler.advanceUntilIdle()

        expectedRepositoryJson = """
            {
                "${person.id}": {
                    "type": "Person",
                    "id": ${person.id},
                    "name": "John Namechanged",
                    "money": ${person.money},
                    "morals": ${person.morals}
                }
            }"""
        jsonFile.readText().shouldEqualJson(expectedRepositoryJson)
    }

    "Man repository is initialized from existing json data and persist new elements into it" {
        val man = Man(1, "John Doe", 123456789L, true)
        jsonFile.writeText(
            """
            {
                "${man.id}": {
                    "type": "Man",
                    "id": ${man.id},
                    "name": "${man.name}",
                    "money": ${man.money},
                    "beard": ${man.beard}
                }
            }"""
        )

        val manRepository = ManGenericJsonFileRepository(jsonFile)
        manRepository.size() shouldBe 1
        manRepository.findById(man.id) shouldBePresent { it shouldBe man }

        manRepository.addOrReplaceAll(
            setOf(
                Man(1, "John Namechanged", 0L, true), Man(2, "Marie", 23L, false)
            )
        )

        testDispatcher.scheduler.advanceUntilIdle()
        val expectedRepositoryJson = """
            {
                "1": {
                    "type": "Man",
                    "id": 1,
                    "name": "John Namechanged",
                    "money": 0,
                    "beard": true
                },
                "2": {
                    "type": "Man",
                    "id": 2,
                    "name": "Marie",
                    "money": 23,
                    "beard": false
                }
            }"""
        expectedRepositoryJson.shouldEqualJson(jsonFile.readText())
    }

    "Concurrent additions should maintain correct repository state" {
        val testPeople = (1..100).map { arbitraryPerson(it).next() }

        // Launch concurrent additions
        testScope.launch {
            testPeople.chunked(10).forEach { chunk ->
                launch {
                    chunk.forEach { person ->
                        repository.add(person)
                    }
                }
            }
        }

        // Advance time to allow operations to complete
        testDispatcher.scheduler.advanceUntilIdle()

        // Verify all people were added correctly
        repository.size() shouldBe testPeople.size
        testPeople.forEach { person ->
            repository.findById(person.id).isPresent shouldBe true
        }

        // Verify serialization occurred
        val reloadedRepo = PersonJsonFileRepository(jsonFile)
        reloadedRepo.size() shouldBe testPeople.size
        testPeople.forEach { person ->
            reloadedRepo.findById(person.id).isPresent shouldBe true
        }
    }

    "Events should be published correctly during concurrent operations" {
        val events = Collections.synchronizedList(mutableListOf<CrudEvent<Int, Personly>>())
        val subscription: TransEventSubscription<in Personly> = repository.subscribe(CREATE) { events.add(it) }

        val testPeople = (1..5_000).map { arbitraryPerson(it).next() }.distinct()

        testPeople.chunked(500).map { chunk ->
            testScope.launch {
                chunk.forEach { person ->
                    repository.add(person)
                }
            }
        }

        testDispatcher.scheduler.advanceUntilIdle()

        // Verify all entities in create events match original entities
        eventually(1.seconds) {
            val createdEntityIds = events.flatMap { it.entities.keys }
            createdEntityIds shouldContainAll testPeople.map { it.id }

            subscription.source.changes shouldBe repository.changes
        }

        PersonJsonFileRepository(jsonFile).size() shouldBe repository.size()
    }

    "Repository should maintain consistency under stress" {
        val operations =
            listOf(
                "add", "remove", "addOrReplace", "removeAll", "addOrReplaceAll"
            )

        val expectedEntities = ConcurrentHashMap<Int, Person>()
        val random = Random(42) // Fixed seed for reproducibility

        // Launch multiple coroutines performing random operations
        val jobs =
            (1..10).map { coroutineId ->
                testScope.launch {
                    repeat(5_000) {
                        val personId = random.nextInt(200)
                        val person = arbitraryPerson(personId).next()

                        when (operations.random(random)) {
                            "add" -> {
                                if (repository.add(person)) {
                                    expectedEntities[personId] = person
                                }
                            }

                            "remove" -> {
                                if (repository.remove(person)) {
                                    expectedEntities.remove(personId)
                                }
                            }

                            "addOrReplace" -> {
                                repository.addOrReplace(person)
                                expectedEntities[personId] = person
                            }

                            "removeAll" -> {
                                if (repository.removeAll(setOf(person))) {
                                    expectedEntities.remove(personId)
                                }
                            }

                            "addOrReplaceAll" -> {
                                repository.addOrReplaceAll(setOf(person))
                                expectedEntities[personId] = person
                            }
                        }
                    }
                }
            }

        // Advance time to let operations complete
        testDispatcher.scheduler.advanceUntilIdle()

        // Verify repository state matches expected state
        repository.size() shouldBe expectedEntities.size
        expectedEntities.forEach { (id, person) ->
            repository.findById(id) shouldBePresent { it shouldBe person }
        }

        // Verify serialization maintained consistency
        val reloadedRepo = PersonJsonFileRepository(jsonFile)
        reloadedRepo.size() shouldBe expectedEntities.size
        expectedEntities.forEach { (id, person) ->
            repository.findById(id) shouldBePresent { it shouldBe person }
        }
    }

    "Repository should handle file I/O failures gracefully" {
        mockkStatic("kotlin.io.FilesKt__FileReadWriteKt")
        mockkStatic("kotlin.io.FilesKt__UtilsKt")

        val testFile =
            mockk<File> {
                every { exists() } returns true
                every { canWrite() } returns true
                every { extension } returns "json"
                every { readText() } returns "{}"
            }

        val ioFailureRepo = PersonJsonFileRepository(testFile)

        // Make the file unwritable after initialization
        every { testFile.canWrite() } throws IOException("Simulated write failure")

        // Add a person - should not throw exception despite I/O failure
        val person = arbitraryPerson(1).next()
        shouldNotThrowAny {
            ioFailureRepo.add(person)
        }

        // Memory state should still be updated
        ioFailureRepo.findById(person.id).isPresent shouldBe true

        // Log should contain error information
        // This would require a test logger or log observer
    }
})