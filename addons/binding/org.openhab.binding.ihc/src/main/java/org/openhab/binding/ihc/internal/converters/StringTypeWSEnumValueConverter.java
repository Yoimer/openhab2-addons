/**
 * Copyright (c) 2010-2018 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.ihc.internal.converters;

import org.eclipse.smarthome.core.library.types.StringType;
import org.openhab.binding.ihc.internal.ws.projectfile.IhcEnumValue;
import org.openhab.binding.ihc.internal.ws.resourcevalues.WSEnumValue;

/**
 * StringType <-> WSEnumValue converter.
 *
 * @author Pauli Anttila - Initial contribution
 */
public class StringTypeWSEnumValueConverter implements Converter<WSEnumValue, StringType> {

    @Override
    public StringType convertFromResourceValue(WSEnumValue from, ConverterAdditionalInfo convertData)
            throws NumberFormatException {
        return new StringType(from.getEnumName());
    }

    @Override
    public WSEnumValue convertFromOHType(StringType from, WSEnumValue value, ConverterAdditionalInfo convertData)
            throws NumberFormatException {
        if (convertData.getEnumValues() != null) {
            boolean found = false;
            for (IhcEnumValue item : convertData.getEnumValues()) {
                if (item.getName().equals(from.toString())) {
                    value.setEnumValueID(item.getId());
                    value.setEnumName(item.getName());
                    found = true;
                    break;
                }
            }
            if (!found) {
                throw new NumberFormatException("Can't find enum value for string " + value.toString());
            }
            return value;
        } else {
            throw new NumberFormatException("Enum list is null");
        }
    }
}
