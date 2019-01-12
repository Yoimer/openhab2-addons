/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.ihc.internal.profiles;

import java.math.BigDecimal;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.core.library.types.IncreaseDecreaseType;
import org.eclipse.smarthome.core.library.types.NextPreviousType;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.PlayPauseType;
import org.eclipse.smarthome.core.library.types.RewindFastforwardType;
import org.eclipse.smarthome.core.library.types.StopMoveType;
import org.eclipse.smarthome.core.library.types.UpDownType;
import org.eclipse.smarthome.core.thing.profiles.ProfileCallback;
import org.eclipse.smarthome.core.thing.profiles.ProfileContext;
import org.eclipse.smarthome.core.thing.profiles.ProfileTypeUID;
import org.eclipse.smarthome.core.thing.profiles.TriggerProfile;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.State;
import org.openhab.binding.ihc.internal.IhcBindingConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link PushButtonToCommandProfile} transforms push button channel events to commands.
 *
 * @author Pauli Anttila - Initial contribution
 */
@NonNullByDefault
public class PushButtonToCommandProfile implements TriggerProfile {

    private final Logger logger = LoggerFactory.getLogger(PushButtonToCommandProfile.class);

    private static final String PARAM_SHORT_PRESS_COMMAND = "short-press-command";
    private static final String PARAM_LONG_PRESS_COMMAND = "long-press-command";
    private static final String PARAM_LONG_PRESS_TIME = "long-press-time";
    private static final String PARAM_REPEAT_TIME = "repeat-time";
    private static final String PARAM_TIMEOUT_TIME = "timeout";

    private static final Command DEF_SHORT_PRESS_COMMAND = OnOffType.ON;
    private static final Command DEF_LONG_PRESS_COMMAND = IncreaseDecreaseType.INCREASE;
    private static final long DEF_LONG_PRESS_TIME = 1000;
    private static final long DEF_REPEAT_TIME = 200;
    private static final long DEF_TIMEOUT_TIME = 10000;

    private final ProfileCallback callback;

    private final ProfileContext context;

    @Nullable
    private Command shortPressCommand;

    @Nullable
    private Command longPressCommand;

    @Nullable
    private ScheduledFuture<?> dimmFuture;
    @Nullable
    private ScheduledFuture<?> timeoutFuture;

    @Nullable
    private State previousState;

    private long pressedTime = 0;

    private long longPressTime;
    private long repeatTime;
    private long timeout;

    PushButtonToCommandProfile(ProfileCallback callback, ProfileContext context) {
        this.callback = callback;
        this.context = context;

        shortPressCommand = getParamAsCommand(PARAM_SHORT_PRESS_COMMAND, DEF_SHORT_PRESS_COMMAND);
        longPressCommand = getParamAsCommand(PARAM_LONG_PRESS_COMMAND, DEF_LONG_PRESS_COMMAND);
        longPressTime = getParamAsLong(PARAM_LONG_PRESS_TIME, DEF_LONG_PRESS_TIME);
        repeatTime = getParamAsLong(PARAM_REPEAT_TIME, DEF_REPEAT_TIME);
        timeout = getParamAsLong(PARAM_TIMEOUT_TIME, DEF_TIMEOUT_TIME);
    }

    private long getParamAsLong(String param, long defValue) {
        long retval;
        Object paramValue = context.getConfiguration().get(param);
        logger.debug("Configuring profile with {} parameter '{}'", param, paramValue);
        if (paramValue instanceof BigDecimal) {
            retval = ((BigDecimal) paramValue).longValue();
        } else {
            logger.debug("Parameter '{}' is not of type BigDecimal, using default value '{}'", param, defValue);
            retval = defValue;
        }
        return retval;
    }

    private @Nullable Command getParamAsCommand(String param, Command defValue) {
        Command retval;
        Object paramValue = context.getConfiguration().get(param);
        logger.debug("Configuring profile with {} parameter '{}'", param, paramValue);

        if (paramValue instanceof String) {
            try {
                retval = convertStringToCommand(String.valueOf(paramValue));
            } catch (IllegalArgumentException e) {
                logger.warn("Parameter '{}' is not a valid command type, using default value '{}'", param, defValue);
                retval = defValue;
            }

        } else {
            logger.debug("Parameter '{}' is not of type String, using default value '{}'", param, defValue);
            retval = defValue;
        }

        return retval;
    }

    private @Nullable Command convertStringToCommand(String str) throws IllegalArgumentException {
        Command retval = null;
        switch (str) {
            case "ON":
                retval = OnOffType.ON;
                break;
            case "OFF":
                retval = OnOffType.OFF;
                break;
            case "STOP":
                retval = StopMoveType.STOP;
                break;
            case "MOVE":
                retval = StopMoveType.MOVE;
                break;
            case "PLAY":
                retval = PlayPauseType.PLAY;
                break;
            case "PAUSE":
                retval = PlayPauseType.PAUSE;
                break;
            case "NEXT":
                retval = NextPreviousType.NEXT;
                break;
            case "PREVIOUS":
                retval = NextPreviousType.PREVIOUS;
                break;
            case "FASTFORWARD":
                retval = RewindFastforwardType.FASTFORWARD;
                break;
            case "REWIND":
                retval = RewindFastforwardType.REWIND;
                break;
            case "INCREASE":
                retval = IncreaseDecreaseType.INCREASE;
                break;
            case "DECREASE":
                retval = IncreaseDecreaseType.DECREASE;
                break;
            case "UP":
                retval = UpDownType.UP;
                break;
            case "DOWN":
                retval = UpDownType.DOWN;
                break;
            case "TOGGLE":
                break; // return null

            default:
                throw new IllegalArgumentException("Illegal argument '" + str + "'");
        }
        return retval;
    }

    @Override
    public ProfileTypeUID getProfileTypeUID() {
        return IhcProfiles.PUSHBUTTON_COMMAND;
    }

    @Override
    public void onStateUpdateFromItem(State state) {
        previousState = state;
    }

    @Override
    public void onTriggerFromHandler(String event) {
        if (IhcBindingConstants.EVENT_PRESSED.equals(event)) {
            buttonPressed(toggleCommandIfNeeded(longPressCommand));
        } else if (IhcBindingConstants.EVENT_RELEASED.equals(event)) {
            buttonReleased(toggleCommandIfNeeded(shortPressCommand));
        }
    }

    private Command toggleCommandIfNeeded(@Nullable Command command) {
        if (command == null) {
            logger.debug("Toggling command: previous state is '{}'", previousState);
            return OnOffType.ON.equals(previousState) ? OnOffType.OFF : OnOffType.ON;
        }
        return command;
    }

    private synchronized void buttonPressed(Command commandToSend) {
        if (timeoutFuture != null) {
            timeoutFuture.cancel(false);
        }

        cancelDimmFuture();

        if (repeatTime > 0) {
            // run repeatedly
            dimmFuture = context.getExecutorService().scheduleWithFixedDelay(() -> callback.sendCommand(commandToSend),
                    longPressTime + 50, repeatTime, TimeUnit.MILLISECONDS);
            timeoutFuture = context.getExecutorService().schedule(() -> this.cancelDimmFuture(), timeout,
                    TimeUnit.MILLISECONDS);
        } else {
            // run only ones
            dimmFuture = context.getExecutorService().schedule(() -> callback.sendCommand(commandToSend),
                    longPressTime + 50, TimeUnit.MILLISECONDS);
        }

        pressedTime = System.currentTimeMillis();
    }

    private synchronized void buttonReleased(Command commandToSend) {
        if (timeoutFuture != null) {
            timeoutFuture.cancel(false);
        }

        this.cancelDimmFuture();

        if (System.currentTimeMillis() - pressedTime <= longPressTime) {
            callback.sendCommand(commandToSend);
        }
    }

    private synchronized void cancelDimmFuture() {
        if (dimmFuture != null) {
            dimmFuture.cancel(false);
        }
    }
}
