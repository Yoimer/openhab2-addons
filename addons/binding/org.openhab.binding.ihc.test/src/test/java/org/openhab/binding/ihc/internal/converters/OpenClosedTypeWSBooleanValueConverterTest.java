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

import org.eclipse.smarthome.core.library.types.OpenClosedType;
import org.eclipse.smarthome.core.types.Type;
import org.junit.Test;
import org.openhab.binding.ihc.internal.ws.resourcevalues.WSBooleanValue;
import org.openhab.binding.ihc.internal.ws.resourcevalues.WSResourceValue;

/**
 * Test for IHC / ELKO binding
 *
 * @author Pauli Anttila - Initial contribution
 */
public class OpenClosedTypeWSBooleanValueConverterTest {

    @Test
    public void testOpen() {
        final boolean inverted = false;
        WSBooleanValue val = new WSBooleanValue(12345);

        val = convertFromOHType(val, OpenClosedType.OPEN, new ConverterAdditionalInfo(null, inverted));
        assertEquals(12345, val.getResourceID());
        assertEquals(true, val.isValue());

        OpenClosedType type = convertFromResourceValue(val, new ConverterAdditionalInfo(null, inverted));
        assertEquals(OpenClosedType.OPEN, type);
    }

    @Test
    public void testClosed() {
        final boolean inverted = false;

        WSBooleanValue val = new WSBooleanValue(12345);
        val = convertFromOHType(val, OpenClosedType.CLOSED, new ConverterAdditionalInfo(null, inverted));
        assertEquals(12345, val.getResourceID());
        assertEquals(false, val.isValue());

        OpenClosedType type = convertFromResourceValue(val, new ConverterAdditionalInfo(null, inverted));
        assertEquals(OpenClosedType.CLOSED, type);
    }

    @Test
    public void testOpenInverted() {
        final boolean inverted = true;

        WSBooleanValue val = new WSBooleanValue(12345);
        val = convertFromOHType(val, OpenClosedType.OPEN, new ConverterAdditionalInfo(null, inverted));
        assertEquals(12345, val.getResourceID());
        assertEquals(false, val.isValue());

        OpenClosedType type = convertFromResourceValue(val, new ConverterAdditionalInfo(null, inverted));
        assertEquals(OpenClosedType.OPEN, type);
    }

    @Test
    public void testClosedInverted() {
        final boolean inverted = true;

        WSBooleanValue val = new WSBooleanValue(12345);
        val = convertFromOHType(val, OpenClosedType.CLOSED, new ConverterAdditionalInfo(null, inverted));
        assertEquals(12345, val.getResourceID());
        assertEquals(true, val.isValue());

        OpenClosedType type = convertFromResourceValue(val, new ConverterAdditionalInfo(null, inverted));
        assertEquals(OpenClosedType.CLOSED, type);
    }

    private WSBooleanValue convertFromOHType(WSBooleanValue IHCvalue, Type OHval,
            ConverterAdditionalInfo converterAdditionalInfo) {
        Converter<WSResourceValue, Type> converter = ConverterFactory.getInstance().getConverter(IHCvalue.getClass(),
                OpenClosedType.class);
        return (WSBooleanValue) converter.convertFromOHType(OHval, IHCvalue, converterAdditionalInfo);
    }

    private OpenClosedType convertFromResourceValue(WSBooleanValue IHCvalue,
            ConverterAdditionalInfo converterAdditionalInfo) {
        Converter<WSResourceValue, Type> converter = ConverterFactory.getInstance().getConverter(IHCvalue.getClass(),
                OpenClosedType.class);
        return (OpenClosedType) converter.convertFromResourceValue(IHCvalue, converterAdditionalInfo);
    }
}
