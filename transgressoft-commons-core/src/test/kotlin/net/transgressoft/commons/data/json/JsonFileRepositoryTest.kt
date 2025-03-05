package net.transgressoft.commons.data.json

import net.transgressoft.commons.data.Man
import net.transgressoft.commons.data.ManGenericJsonFileRepository
import net.transgressoft.commons.data.Person
import net.transgressoft.commons.data.PersonJsonFileRepository
import net.transgressoft.commons.data.arbitraryPerson
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.common.runBlocking
import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempfile
import io.kotest.matchers.optional.shouldBePresent
import io.kotest.matchers.shouldBe
import io.kotest.property.arbitrary.next
import java.io.File
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private lateinit var jsonFile: File
private lateinit var repository: PersonJsonFileRepository

private fun getRandomPerson(ids: MutableCollection<Int>): Person? =
    synchronized(ids) {
        if (ids.isNotEmpty()) {
            ids.random().let {
                ids.remove(it)
                arbitraryPerson(it).next()
            }
        } else null
    }

class JsonFileRepositoryTest : StringSpec({

    beforeEach {
        jsonFile = tempfile("json-repository-test", ".json").also { it.deleteOnExit() }
        repository = PersonJsonFileRepository(file = jsonFile)
    }

    "Repository serializes itself to file when element is added" {
        val person = Person(1, "John Doe", 123456789L, true)
        repository.add(person) shouldBe true

        eventually(200.milliseconds) {
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
            PersonJsonFileRepository(file = jsonFile) shouldBe repository
        }
    }

    "Repository serializes itself to file when element is added or replaced" {
        val person = Person(1, "John Doe", 123456789L, true)
        repository.add(person) shouldBe true

        val person2 = person.copy(initialName = "Ken")
        repository.addOrReplace(person2) shouldBe true

        eventually(100.milliseconds) {
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
            jsonFile.readText().shouldEqualJson(expectedRepositoryJson)

            PersonJsonFileRepository(file = jsonFile) shouldBe repository
        }
    }

    "Repository behaves as expected on concurrent operations" {
        val expectedSize = AtomicInteger(0)
        val randomIds =
            Collections.synchronizedCollection(
                generateSequence { Random.nextInt(10_000) }.distinct().take(5_000).toSet()
            )

        val concurrentOperationsJob =
            runBlocking {
                launch(Dispatchers.Default) {
                    while (randomIds.isNotEmpty()) {
                        getRandomPerson(randomIds)?.apply {
                            repository.add(this).let { added ->
                                if (added) {
                                    expectedSize.incrementAndGet()
                                    randomIds.add(this.id)
                                }
                            }
                        }
                    }
                }
                launch(Dispatchers.Default) {
                    while (randomIds.isNotEmpty()) {
                        getRandomPerson(randomIds)?.apply {
                            repository.removeAll(setOf(this)).let { removed ->
                                if (removed) expectedSize.decrementAndGet()
                            }
                        }
                    }
                }
                launch(Dispatchers.Default) {
                    while (randomIds.isNotEmpty()) {
                        getRandomPerson(randomIds)?.apply {
                            val existed = repository.contains(this.id)
                            repository.addOrReplace(this).let { added ->
                                if (added && !existed) {
                                    expectedSize.incrementAndGet()
                                    randomIds.add(this.id)
                                }
                            }
                        }
                    }
                }
                launch(Dispatchers.Default) {
                    while (randomIds.isNotEmpty()) {
                        getRandomPerson(randomIds)?.apply {
                            repository.remove(this).let { removed ->
                                if (removed) expectedSize.decrementAndGet()
                            }
                        }
                    }
                }
                launch(Dispatchers.Default) {
                    while (randomIds.isNotEmpty()) {
                        getRandomPerson(randomIds)?.apply {
                            val existed = repository.contains(this.id)
                            repository.addOrReplaceAll(setOf(this)).let { added ->
                                if (added && !existed) {
                                    expectedSize.incrementAndGet()
                                    randomIds.add(this.id)
                                }
                            }
                        }
                    }
                }
            }
        concurrentOperationsJob.join()

        eventually(2.seconds) {
            repository.size() shouldBe expectedSize.get()
            PersonJsonFileRepository(file = jsonFile) shouldBe repository
        }

        repository.clear()

        eventually(100.milliseconds) {
            repository.isEmpty shouldBe true
            PersonJsonFileRepository(file = jsonFile) shouldBe repository
        }
    }

    "Person repository is initialized from existing json data and persist new elements into it" {
        val person = Person(1, "John Doe", 123456789L, true)
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

        repository = PersonJsonFileRepository(file = jsonFile)
        repository.size() shouldBe 1
        repository.findById(person.id) shouldBePresent { it shouldBe person }

        repository.addOrReplaceAll(
            setOf(
                Person(1, "John Namechanged", 0L, true),
                Person(2, "Marie", 23L, false)
            )
        )

        eventually(500.milliseconds) {
            val expectedRepositoryJson = """
                {
                    "1": {
                        "type": "Person",
                        "id": 1,
                        "name": "John Namechanged",
                        "money": 0,
                        "morals": true
                    },
                    "2": {
                        "type": "Person",
                        "id": 2,
                        "name": "Marie",
                        "money": 23,
                        "morals": false
                    }
                }"""
            jsonFile.readText().shouldEqualJson(expectedRepositoryJson)
        }
    }

    "Repository cannot be created from a wrong file" {
        shouldThrow<IllegalArgumentException> { PersonJsonFileRepository(file = File("/does-not-exist.txt")) }
            .message shouldBe "Provided jsonFile does not exist, is not writable or is not a json file"
    }

    "Repository can change the file where it is serialized dynamically" {
        val person = Person(1, "John Doe", 123456789L, true)
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

        repository.addOrReplaceAll(
            setOf(
                Person(1, "John Namechanged", 0L, true),
                Person(2, "Marie", 23L, false)
            )
        )

        eventually(100.milliseconds) {
            val expectedRepositoryJson = """
                {
                    "1": {
                        "type": "Person",
                        "id": 1,
                        "name": "John Namechanged",
                        "money": 0,
                        "morals": true
                    },
                    "2": {
                        "type": "Person",
                        "id": 2,
                        "name": "Marie",
                        "money": 23,
                        "morals": false
                    }
                }"""
            expectedRepositoryJson.shouldEqualJson(newJsonFile.readText())
        }
    }

    "Repository serializes itself when entity is modified" {
        val person = Person(1, "John Doe", 123456789L, true)
        repository.add(person)
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

        eventually(100.milliseconds) {
            jsonFile.readText().shouldEqualJson(expectedRepositoryJson)
        }

        person.name = "John Namechanged"
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

        eventually(100.milliseconds) {
            jsonFile.readText().shouldEqualJson(expectedRepositoryJson)
        }
    }

    "Repository serializes itself when entity is modified during an action on the repository" {
        val person = Person(1, "John Doe", 123456789L, true)
        repository.add(person)
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

        eventually(100.milliseconds) {
            jsonFile.readText().shouldEqualJson(expectedRepositoryJson)
        }

        repository.runForSingle(1) { it.name = "John Namechanged" }

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

        eventually(100.milliseconds) {
            jsonFile.readText().shouldEqualJson(expectedRepositoryJson)
        }
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

        val manRepository = ManGenericJsonFileRepository(file = jsonFile)
        manRepository.size() shouldBe 1
        manRepository.findById(man.id) shouldBePresent { it shouldBe man }

        manRepository.addOrReplaceAll(
            setOf(
                Man(1, "John Namechanged", 0L, true),
                Man(2, "Marie", 23L, false)
            )
        )

        eventually(100.milliseconds) {
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
    }
})