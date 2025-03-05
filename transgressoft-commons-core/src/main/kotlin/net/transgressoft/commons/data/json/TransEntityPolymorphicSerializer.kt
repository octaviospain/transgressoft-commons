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

package net.transgressoft.commons.data.json

import net.transgressoft.commons.TransEntity
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.ClassSerialDescriptorBuilder
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.CompositeEncoder
import kotlinx.serialization.encoding.Decoder

interface TransEntityPolymorphicSerializer<T : TransEntity> : KSerializer<T> {
    override fun deserialize(decoder: Decoder): T = createInstance(getPropertiesList(decoder))

    fun additionalElements(classSerialDescriptorBuilder: ClassSerialDescriptorBuilder) {
        // Do nothing by default
    }

    fun additionalSerialize(compositeEncoder: CompositeEncoder, value: T) {
        // Do nothing by default
    }

    fun additionalDeserialize(compositeDecoder: CompositeDecoder, propertiesList: MutableList<Any?>, index: Int) {
        // Do nothing by default
    }

    fun getPropertiesList(decoder: Decoder): List<Any?>

    fun createInstance(propertiesList: List<Any?>): T
}