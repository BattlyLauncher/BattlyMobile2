package net.kdt.pojavlaunch.customcontrols.gamepad;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ControllerAnalogFilterTest {
    @Test
    public void dualSenseRestingNoiseIsCentered() {
        ControllerAnalogFilter.StickState stick = new ControllerAnalogFilter.StickState(1f);
        stick.setX((130f - 127.5f) / 127.5f);
        stick.setY((120f - 127.5f) / 127.5f);

        assertEquals(0f, stick.getX(), 0.0001f);
        assertEquals(0f, stick.getY(), 0.0001f);
    }

    @Test
    public void stickLeavesCenterOnlyAfterActivationThreshold() {
        ControllerAnalogFilter.StickState stick = new ControllerAnalogFilter.StickState(1f);
        stick.setX(0.12f);
        assertEquals(0f, stick.getX(), 0.0001f);

        stick.setX(0.5f);
        assertTrue(stick.getX() > 0.4f);

        stick.setX(0.08f);
        assertEquals(0f, stick.getX(), 0.0001f);
    }

    @Test
    public void digitalAndAnalogTriggerReportsBecomeOnePress() {
        ControllerAnalogFilter.TriggerState trigger = new ControllerAnalogFilter.TriggerState();

        assertTrue(trigger.setDigitalPressed(true));
        assertTrue(trigger.isPressed());
        assertFalse(trigger.setAnalogValue(1f));
        assertFalse(trigger.setDigitalPressed(false));
        assertTrue(trigger.isPressed());
        assertTrue(trigger.setAnalogValue(0f));
        assertFalse(trigger.isPressed());
    }

    @Test
    public void triggerThresholdHasHysteresis() {
        ControllerAnalogFilter.TriggerState trigger = new ControllerAnalogFilter.TriggerState();

        assertFalse(trigger.setAnalogValue(0.6f));
        assertTrue(trigger.setAnalogValue(0.7f));
        assertFalse(trigger.setAnalogValue(0.5f));
        assertFalse(trigger.setAnalogValue(0.4f));
        assertTrue(trigger.setAnalogValue(0.3f));
    }
}
