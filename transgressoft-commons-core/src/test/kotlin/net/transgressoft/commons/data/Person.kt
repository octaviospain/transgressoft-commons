package net.transgressoft.commons.data

import net.transgressoft.commons.EventType
import net.transgressoft.commons.ReactiveEntity
import net.transgressoft.commons.ReactiveEntityBase
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.positiveInt
import io.kotest.property.arbitrary.stringPattern
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

interface Human<H : Human<H>> : ReactiveEntity<Int, H> {
    override val id: Int
    var name: String?
    val money: Long?
}

interface Personly : Human<Personly> {
    val morals: Boolean
}

@Serializable
@SerialName("Person")
data class Person(
    override val id: Int,
    @SerialName("name") private var initialName: String? = null,
    override var money: Long?,
    override val morals: Boolean
) : Personly, ReactiveEntityBase<Int, Personly>() {
    @Transient
    override var name: String? = initialName
        get() = initialName
        set(value) {
            setAndNotify(value, field) { initialName = value }
        }

    override val uniqueId: String
        get() = "$id-$name-$money-$morals"

    override fun clone(): Person = copy()

    override fun toString(): String = "Person(id=$id, name=$name, money=$money, morals=$morals)"
}

sealed class PersonEventType {
    enum class Type(override val code: Int) : EventType {
        BORN(201)
    }

    private data class Born<T : Personly>(val genre: Boolean, override val entities: Map<Int, Personly>) : CrudEvent<Int, Personly> {
        override val type: EventType = Type.BORN
    }
}

private val defaultId = -1

fun arbitraryPerson(id: Int = defaultId) =
    arbitrary {
        Person(
            id = if (id == defaultId) Arb.positiveInt(500_000).bind() else id,
            initialName = Arb.stringPattern("[a-z]{5} [a-z]{5}").bind(),
            money = Arb.long(0, Long.MAX_VALUE - 1).bind(),
            morals = Arb.boolean().bind()
        )
    }