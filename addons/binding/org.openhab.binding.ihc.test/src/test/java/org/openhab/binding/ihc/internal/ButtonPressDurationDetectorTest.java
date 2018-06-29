/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.binding.ihc.internal;

import static org.junit.Assert.assertEquals;

import java.time.Duration;

import org.junit.Test;

/**
 * Test for IHC / ELKO binding
 *
 * @author Pauli Anttila
 */
public class ButtonPressDurationDetectorTest {

    @Test
    public void TestShortPress() {
        Duration duration = Duration.ofMillis(450);
        long short_press_max_time = 1000;
        long long_press_max_time = 2000;
        long extra_long_press_max_time = 4000;

        ButtonPressDurationDetector button = new ButtonPressDurationDetector(duration, short_press_max_time,
                long_press_max_time, extra_long_press_max_time);

        assertEquals(true, button.isShortPress());
        assertEquals(false, button.isLongPress());
        assertEquals(false, button.isExtraLongPress());
    }

    @Test
    public void TestLongPress() {
        Duration duration = Duration.ofMillis(1003);
        long short_press_max_time = 1000;
        long long_press_max_time = 2000;
        long extra_long_press_max_time = 4000;

        ButtonPressDurationDetector button = new ButtonPressDurationDetector(duration, short_press_max_time,
                long_press_max_time, extra_long_press_max_time);

        assertEquals(false, button.isShortPress());
        assertEquals(true, button.isLongPress());
        assertEquals(false, button.isExtraLongPress());
    }

    @Test
    public void TestExtraLongPress() {
        Duration duration = Duration.ofMillis(2423);
        long short_press_max_time = 1000;
        long long_press_max_time = 2000;
        long extra_long_press_max_time = 4000;

        ButtonPressDurationDetector button = new ButtonPressDurationDetector(duration, short_press_max_time,
                long_press_max_time, extra_long_press_max_time);

        assertEquals(false, button.isShortPress());
        assertEquals(false, button.isLongPress());
        assertEquals(true, button.isExtraLongPress());
    }

    @Test
    public void TestTooLongPress() {
        Duration duration = Duration.ofMillis(5001);
        long short_press_max_time = 1000;
        long long_press_max_time = 2000;
        long extra_long_press_max_time = 4000;

        ButtonPressDurationDetector button = new ButtonPressDurationDetector(duration, short_press_max_time,
                long_press_max_time, extra_long_press_max_time);

        assertEquals(false, button.isShortPress());
        assertEquals(false, button.isLongPress());
        assertEquals(false, button.isExtraLongPress());
    }
}
