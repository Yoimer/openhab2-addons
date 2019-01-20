/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.ihc.internal.converters;

import static org.junit.Assert.assertEquals;

import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.types.Type;
import org.junit.Test;
import org.openhab.binding.ihc.internal.ws.resourcevalues.WSBooleanValue;
import org.openhab.binding.ihc.internal.ws.resourcevalues.WSResourceValue;

/**
 * Test for IHC / ELKO binding
 *
 * @author Pauli Anttila - Initial contribution
 */
public class DecimalTypeWSBooleanValueConverterTest {

    @Test
    public void testOn() {
        final boolean inverted = false;
        WSBooleanValue val = new WSBooleanValue(12345);

        val = convertFromOHType(val, new DecimalType(1), new ConverterAdditionalInfo(null, inverted));
        assertEquals(12345, val.getResourceID());
        assertEquals(true, val.booleanValue());

        DecimalType type = convertFromResourceValue(val, new ConverterAdditionalInfo(null, inverted));
        assertEquals(new DecimalType(1), type);
    }

    @Test
    public void testOff() {
        final boolean inverted = false;

        WSBooleanValue val = new WSBooleanValue(12345);
        val = convertFromOHType(val, new DecimalType(0), new ConverterAdditionalInfo(null, inverted));
        assertEquals(12345, val.getResourceID());
        assertEquals(false, val.booleanValue());

        DecimalType type = convertFromResourceValue(val, new ConverterAdditionalInfo(null, inverted));
        assertEquals(new DecimalType(0), type);
    }

    private WSBooleanValue convertFromOHType(WSBooleanValue IHCvalue, Type OHval,
            ConverterAdditionalInfo converterAdditionalInfo) {
        Converter<WSResourceValue, Type> converter = ConverterFactory.getInstance().getConverter(IHCvalue.getClass(),
                DecimalType.class);
        return (WSBooleanValue) converter.convertFromOHType(OHval, IHCvalue, converterAdditionalInfo);
    }

    private DecimalType convertFromResourceValue(WSBooleanValue IHCvalue,
            ConverterAdditionalInfo converterAdditionalInfo) {
        Converter<WSResourceValue, Type> converter = ConverterFactory.getInstance().getConverter(IHCvalue.getClass(),
                DecimalType.class);
        return (DecimalType) converter.convertFromResourceValue(IHCvalue, converterAdditionalInfo);
    }
}
