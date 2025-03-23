package net.transgressoft.commons

import net.transgressoft.commons.entity.ReactiveEntityBase
import java.io.File
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.ClassSerialDescriptorBuilder
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.CompositeEncoder

interface Manly : Human<Manly> {
    val beard: Boolean
}

@Serializable
@SerialName("Man")
data class Man(
    override val id: Int,
    override var name: String?,
    override val money: Long?,
    override val beard: Boolean = false
) : Manly, ReactiveEntityBase<Int, Manly>() {
    override val uniqueId: String = "$id-$name-$money"

    override fun clone(): Man = copy()
}

open class ManGenericJsonFileRepository(file: File) :
    HumanGenericJsonFileRepositoryBase<Manly>("ManRepo", file, ManlySerializer())

class ManlySerializer : HumanSerializer<Manly>() {
    override fun additionalElements(classSerialDescriptorBuilder: ClassSerialDescriptorBuilder) {
        classSerialDescriptorBuilder.element<Boolean>("beard")
    }

    override fun additionalSerialize(compositeEncoder: CompositeEncoder, value: Manly) {
        compositeEncoder.encodeBooleanElement(descriptor, 4, value.beard)
    }

    override fun additionalDeserialize(compositeDecoder: CompositeDecoder, propertiesList: MutableList<Any?>, index: Int) {
        if (index == 4) {
            propertiesList.add(compositeDecoder.decodeBooleanElement(descriptor, 4))
        }
    }

    override fun createInstance(propertiesList: List<Any?>): Manly =
        Man(propertiesList[0] as Int, propertiesList[1] as String?, propertiesList[2] as Long?, propertiesList[3] as Boolean)
}