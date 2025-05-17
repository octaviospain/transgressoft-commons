package net.transgressoft.commons.persistence.json

import io.kotest.assertions.json.shouldEqualJson
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempfile
import io.kotest.matchers.optional.shouldBePresent
import io.kotest.matchers.shouldBe
import java.io.File
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.ExperimentalCoroutinesApi

@ExperimentalCoroutinesApi
class FlexibleJsonFileRepositoryTest : StringSpec({

    lateinit var repository: FlexibleJsonFileRepository
    lateinit var jsonFile: File

    beforeEach {
        jsonFile = tempfile("json-repository-test", ".json").also { it.deleteOnExit() }
        repository = FlexibleJsonFileRepository(jsonFile)
    }

    afterEach {
        repository.close()
    }

    "Repository should create and serialize strings, booleans and integers" {
        repository = FlexibleJsonFileRepository(jsonFile)
        val reactiveString = repository.createReactiveString("id1", "value1")
        val reactiveBoolean = repository.createReactiveBoolean("id2", true)
        val reactiveInt = repository.createReactiveInt("id3", 3)

        repository.contains(reactiveString.id) shouldBe true
        repository.findByUniqueId(reactiveBoolean.uniqueId) shouldBePresent { it shouldBe reactiveBoolean }
        repository.findFirst { it.value == 3 } shouldBePresent { it shouldBe reactiveInt }

        eventually(500.milliseconds) {
            jsonFile.readText().shouldEqualJson(
                """
                {
                    "id1": "value1",
                    "id2": true,
                    "id3": 3
                }"""
            )
        }
    }

    "Repository should be created from a json string of string, booleans and integers" {
        jsonFile.writeText(
            """
            {
                "property.1": "thevalue",
                "has_value": true,
                "number_of": 3
            }"""
        )

        val repository = FlexibleJsonFileRepository(jsonFile)

        repository.contains("property.1") shouldBe true
        repository.findByUniqueId("has_value-true") shouldBePresent { it.value shouldBe true }
        repository.findFirst { it.value == 3 } shouldBePresent { it.id shouldBe "number_of" }
    }
})