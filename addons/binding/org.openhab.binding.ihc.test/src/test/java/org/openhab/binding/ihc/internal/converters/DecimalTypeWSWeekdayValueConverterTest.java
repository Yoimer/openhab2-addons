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
import org.openhab.binding.ihc.internal.ws.resourcevalues.WSResourceValue;
import org.openhab.binding.ihc.internal.ws.resourcevalues.WSWeekdayValue;

/**
 * Test for IHC / ELKO binding
 *
 * @author Pauli Anttila - Initial contribution
 */
public class DecimalTypeWSWeekdayValueConverterTest {

    @Test
    public void Test() {
        WSWeekdayValue val = new WSWeekdayValue(12345);

        val = convertFromOHType(val, new DecimalType(6), new ConverterAdditionalInfo(null, false));
        assertEquals(12345, val.getResourceID());
        assertEquals(6, val.getWeekdayNumber());

        DecimalType type = convertFromResourceValue(val, new ConverterAdditionalInfo(null, false));
        assertEquals(new DecimalType(6), type);
    }

    private WSWeekdayValue convertFromOHType(WSWeekdayValue IHCvalue, Type OHval,
            ConverterAdditionalInfo converterAdditionalInfo) {
        Converter<WSResourceValue, Type> converter = ConverterFactory.getInstance().getConverter(IHCvalue.getClass(),
                DecimalType.class);
        return (WSWeekdayValue) converter.convertFromOHType(OHval, IHCvalue, converterAdditionalInfo);
    }

    private DecimalType convertFromResourceValue(WSWeekdayValue IHCvalue,
            ConverterAdditionalInfo converterAdditionalInfo) {
        Converter<WSResourceValue, Type> converter = ConverterFactory.getInstance().getConverter(IHCvalue.getClass(),
                DecimalType.class);
        return (DecimalType) converter.convertFromResourceValue(IHCvalue, converterAdditionalInfo);
    }
}
