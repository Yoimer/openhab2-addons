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

import org.eclipse.smarthome.core.library.types.OpenClosedType;
import org.openhab.binding.ihc.internal.ws.resourcevalues.WSIntegerValue;

/**
 * OpenClosedType <-> WSIntegerValue converter.
 *
 * @author Pauli Anttila - Initial contribution
 */
public class OpenClosedTypeWSIntegerValueConverter implements Converter<WSIntegerValue, OpenClosedType> {

    @Override
    public OpenClosedType convertFromResourceValue(WSIntegerValue from, ConverterAdditionalInfo convertData)
            throws NumberFormatException {
        return from.getInteger() > 0 ^ convertData.getInverted() ? OpenClosedType.OPEN : OpenClosedType.CLOSED;
    }

    @Override
    public WSIntegerValue convertFromOHType(OpenClosedType from, WSIntegerValue value,
            ConverterAdditionalInfo convertData) throws NumberFormatException {
        int newVal = from == OpenClosedType.OPEN ? 1 : 0;

        if (convertData.getInverted()) {
            newVal = newVal == 1 ? 0 : 1;
        }
        if (newVal >= value.getMinimumValue() && newVal <= value.getMaximumValue()) {
            value.setInteger(newVal);
            return value;
        } else {
            throw new NumberFormatException("Value is not between accetable limits (min=" + value.getMinimumValue()
                    + ", max=" + value.getMaximumValue() + ")");
        }
    }
}
