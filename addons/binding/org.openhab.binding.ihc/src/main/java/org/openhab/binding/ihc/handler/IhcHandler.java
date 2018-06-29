/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.ihc.handler;

import static org.openhab.binding.ihc.IhcBindingConstants.*;

import java.io.File;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.smarthome.core.library.types.DateTimeType;
import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.StringType;
import org.eclipse.smarthome.core.thing.Channel;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandler;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.RefreshType;
import org.eclipse.smarthome.core.types.State;
import org.eclipse.smarthome.core.types.Type;
import org.openhab.binding.ihc.internal.ButtonPressDurationDetector;
import org.openhab.binding.ihc.internal.ChannelUtils;
import org.openhab.binding.ihc.internal.EnumDictionary;
import org.openhab.binding.ihc.internal.SignalLevelConverter;
import org.openhab.binding.ihc.internal.config.ChannelParams;
import org.openhab.binding.ihc.internal.config.IhcConfiguration;
import org.openhab.binding.ihc.internal.converters.Converter;
import org.openhab.binding.ihc.internal.converters.ConverterAdditionalInfo;
import org.openhab.binding.ihc.internal.converters.ConverterFactory;
import org.openhab.binding.ihc.ws.IhcClient;
import org.openhab.binding.ihc.ws.IhcClient.ConnectionState;
import org.openhab.binding.ihc.ws.IhcEventListener;
import org.openhab.binding.ihc.ws.datatypes.WSControllerState;
import org.openhab.binding.ihc.ws.datatypes.WSRFDevice;
import org.openhab.binding.ihc.ws.datatypes.WSSystemInfo;
import org.openhab.binding.ihc.ws.exeptions.IhcExecption;
import org.openhab.binding.ihc.ws.projectfile.IhcEnumValue;
import org.openhab.binding.ihc.ws.projectfile.ProjectFileUtils;
import org.openhab.binding.ihc.ws.resourcevalues.WSBooleanValue;
import org.openhab.binding.ihc.ws.resourcevalues.WSEnumValue;
import org.openhab.binding.ihc.ws.resourcevalues.WSResourceValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

/**
 * The {@link IhcHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Pauli Anttila - Initial contribution
 */
public class IhcHandler extends BaseThingHandler implements IhcEventListener {
    private final Logger logger = LoggerFactory.getLogger(IhcHandler.class);

    private static final int MAX_PULSE_WIDTH_IN_MS = 4000;

    private static final String LOCAL_IHC_PROJECT_FILE_NAME_TEMPLATE = "ihc-project-file-%s.xml";

    /** Holds runtime notification reorder timeout in milliseconds */
    private final int NOTIFICATIONS_REORDER_WAIT_TIME = 2000;

    /** IHC / ELKO LS Controller client */
    private static IhcClient ihc;

    /**
     * Reminder to slow down resource value notification ordering from
     * controller.
     */
    private NotificationsRequestReminder reminder = null;
    private boolean reconnectRequest = false;
    private boolean valueNotificationRequest = false;

    private ScheduledFuture<?> controlJob;
    private ScheduledFuture<?> pollingJobRf;

    Document projectFile;

    private boolean connecting = false;

    /**
     * Store current state of the controller, use to recognize when controller
     * state is changed
     */
    private String controllerState = "";

    private IhcConfiguration conf;

    private final Set<Integer> linkedResourceIds = Collections.synchronizedSet(new HashSet<>());

    private Map<Integer, LocalDateTime> lastUpdate = new HashMap<>();

    private EnumDictionary enumDictionary;

    private final Runnable pollingRunnableRF = new Runnable() {
        @Override
        public void run() {
            updateRfDeviceStates();
        }
    };

    private final Runnable controlRunnable = new Runnable() {
        @Override
        public void run() {
            reconnectCheck();
        }
    };

    public IhcHandler(Thing thing) {
        super(thing);
    }

    protected boolean isValueNotificationRequestActivated() {
        synchronized (this) {
            return valueNotificationRequest;
        }
    }

    protected void setValueNotificationRequest(boolean valueNotificationRequest) {
        synchronized (this) {
            this.valueNotificationRequest = valueNotificationRequest;
        }
    }

    protected boolean isReconnectRequestActivated() {
        synchronized (this) {
            return reconnectRequest;
        }
    }

    protected void setReconnectRequest(boolean reconnect) {
        synchronized (this) {
            this.reconnectRequest = reconnect;
        }
    }

    protected boolean isConnecting() {
        synchronized (this) {
            return connecting;
        }
    }

    protected void setConnectingState(boolean value) {
        synchronized (this) {
            this.connecting = value;
        }
    }

    private String getFilePathInUserDataFolder(String fileName) {
        String progArg = System.getProperty("smarthome.userdata");
        if (progArg != null) {
            return progArg + File.separator + fileName;
        }
        return fileName;
    }

    @Override
    public void initialize() {
        conf = getConfigAs(IhcConfiguration.class);
        logger.debug("Using configuration: {}", conf);

        if (controlJob == null || controlJob.isCancelled()) {
            logger.debug("Start control task, interval={}sec", 1);
            controlJob = scheduler.scheduleWithFixedDelay(controlRunnable, 0, 1, TimeUnit.SECONDS);
        }
    }

    @Override
    public void dispose() {
        logger.debug("Stopping thing");
        if (controlJob != null && !controlJob.isCancelled()) {
            controlJob.cancel(true);
            controlJob = null;
        }
        disconnect();
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        logger.debug("Received channel: {}, command: {}", channelUID, command);

        if (ihc == null) {
            logger.warn("Connection is not initialized, abort resource value update for channel '{}'!", channelUID);
            return;
        }

        if (ihc.getConnectionState() != ConnectionState.CONNECTED) {
            logger.warn("Connection to controller is not open, abort resource value update for channel '{}'!",
                    channelUID);
            return;
        }

        switch (channelUID.getId()) {
            case CHANNEL_CONTROLLER_STATE:
                if (command.equals(RefreshType.REFRESH)) {
                    updateControllerStateChannel();
                }
                break;

            case CHANNEL_CONTROLLER_UPTIME:
            case CHANNEL_CONTROLLER_TIME:
            case CHANNEL_CONTROLLER_SW_VERSION:
            case CHANNEL_CONTROLLER_HW_VERSION:
                if (command.equals(RefreshType.REFRESH)) {
                    updateControllerInformationChannels();
                }
                break;

            default:
                if (command.equals(RefreshType.REFRESH)) {
                    refreshChannel(channelUID);
                } else {
                    updateResourceChannel(channelUID, command);
                }
                break;
        }
    }

    private void refreshChannel(ChannelUID channelUID) {
        logger.debug("REFRESH channel {}", channelUID);
        try {
            ChannelParams params = new ChannelParams(thing.getChannel(channelUID.getId()));
            logger.debug("Channel params: {}", params);
            if (params.isDirectionWriteOnly()) {
                logger.warn("Write only channel, skip refresh command to {}", channelUID);
                return;
            }
            WSResourceValue value = ihc.resourceQuery(params.getResourceId());
            resourceValueUpdateReceived(value);
        } catch (IhcExecption e) {
            logger.error("Can't update channel '{}' value, cause ", channelUID, e.getMessage());
        } catch (IllegalArgumentException e) {
            logger.warn("Can't find resource id, reason {}", e.getMessage());
        }
    }

    private void updateControllerStateChannel() {
        try {
            String state = ihc.getControllerState().getState();
            String value;

            switch (state) {
                case IhcClient.CONTROLLER_STATE_INITIALIZE:
                    value = "Initialize";
                    break;
                case IhcClient.CONTROLLER_STATE_READY:
                    value = "Ready";
                    break;
                default:
                    value = "Unknown state: " + state;
            }

            updateState(new ChannelUID(getThing().getUID(), CHANNEL_CONTROLLER_STATE), new StringType(value));
        } catch (IhcExecption e) {
            logger.warn("Controller state information fetch failed, reason {}", e.getMessage());
        }
    }

    private void updateControllerInformationChannels() {
        try {
            WSSystemInfo systemInfo = ihc.getSystemInfo();
            logger.debug("Controller information: {}", systemInfo);

            updateState(new ChannelUID(getThing().getUID(), CHANNEL_CONTROLLER_SW_VERSION),
                    new StringType(systemInfo.getVersion()));
            updateState(new ChannelUID(getThing().getUID(), CHANNEL_CONTROLLER_HW_VERSION),
                    new StringType(systemInfo.getHwRevision()));
            updateState(new ChannelUID(getThing().getUID(), CHANNEL_CONTROLLER_UPTIME),
                    new DecimalType((double) systemInfo.getUptime() / 1000));
            updateState(new ChannelUID(getThing().getUID(), CHANNEL_CONTROLLER_TIME),
                    new DateTimeType(systemInfo.getRealTimeClock().atZone(ZoneId.systemDefault())));
        } catch (IhcExecption e) {
            logger.warn("Controller uptime information fetch failed, reason {}", e.getMessage());
        }
    }

    private void updateResourceChannel(ChannelUID channelUID, Command command) {
        try {
            Channel channel = thing.getChannel(channelUID.getId());
            if (channel != null) {
                ChannelParams params = new ChannelParams(channel);
                logger.debug("Channel params: {}", params);
                if (params.isDirectionReadOnly()) {
                    logger.warn("Read only channel, skip the update to {}", channelUID);
                    return;
                }
                updateChannel(channelUID, params, command);
            }
        } catch (IhcExecption e) {
            logger.error("Can't update channel '{}' value, cause ", channelUID, e.getMessage());
        } catch (IllegalArgumentException e) {
            logger.warn("Can't find resource id, reason {}", e.getMessage());
        }
    }

    private void updateChannel(ChannelUID channelUID, ChannelParams params, Command command)
            throws IllegalArgumentException, IhcExecption {

        if (params.getCommandToReact() != null) {
            if (command.toString().equals(params.getCommandToReact())) {
                logger.debug("Command '{}' equal to channel reaction parameter '{}', execute it", command,
                        params.getCommandToReact());
            } else {
                logger.debug("Command '{}' doesn't equal to reaction trigger parameter '{}', skip it", command,
                        params.getCommandToReact());
                return;
            }
        }
        WSResourceValue value = ihc.getResourceValueInformation(params.getResourceId());
        if (value != null) {
            if (params.getPulseWidth() != null) {
                sendPulseCommand(channelUID, params, value, Math.min(params.getPulseWidth(), MAX_PULSE_WIDTH_IN_MS));
            } else {
                sendNormalCommand(channelUID, params, command, value);
            }
        }
    }

    private void sendNormalCommand(ChannelUID channelUID, ChannelParams params, Command command, WSResourceValue value)
            throws IhcExecption {

        logger.debug("Send command '{}' to resource '{}'", command, value.getResourceID());
        ConverterAdditionalInfo converterAdditionalInfo = new ConverterAdditionalInfo(getEnumValues(value),
                params.isInverted());
        Converter<WSResourceValue, Type> converter = ConverterFactory.getInstance().getConverter(value.getClass(),
                command.getClass());
        WSResourceValue val = converter.convertFromOHType(command, value, converterAdditionalInfo);
        logger.debug("Update resource value (inverted output={}): {}", params.isInverted(), val);
        if (!updateResource(val)) {
            logger.warn("Channel {} update to resource '{}' failed.", channelUID, val);
        }
    }

    private ArrayList<IhcEnumValue> getEnumValues(WSResourceValue value) {
        if (value instanceof WSEnumValue) {
            return enumDictionary.getEnumValues(((WSEnumValue) value).getDefinitionTypeID());
        }
        return null;
    }

    private void sendPulseCommand(ChannelUID channelUID, ChannelParams params, WSResourceValue value,
            Integer pulseWidth) throws IhcExecption {

        logger.debug("Send {}ms pulse to resource: {}", pulseWidth, value.getResourceID());
        logger.debug("Channel params: {}", params);
        Converter<WSResourceValue, Type> converter = ConverterFactory.getInstance().getConverter(value.getClass(),
                OnOffType.class);
        ConverterAdditionalInfo converterAdditionalInfo = new ConverterAdditionalInfo(null, params.isInverted());

        // set resource to ON
        WSResourceValue val = converter.convertFromOHType(OnOffType.ON, value, converterAdditionalInfo);
        logger.debug("Update resource value (inverted output={}): {}", params.isInverted(), val);
        if (updateResource(val)) {
            // sleep a while
            try {
                logger.debug("Sleeping: {}ms", pulseWidth);
                Thread.sleep(pulseWidth);
            } catch (InterruptedException e) {
                // do nothing
            }
            // set resource back to OFF
            val = converter.convertFromOHType(OnOffType.OFF, value, converterAdditionalInfo);
            logger.debug("Update resource value (inverted output={}): {}", params.isInverted(), val);
            if (!updateResource(val)) {
                logger.warn("Channel {} update to resource '{}' failed.", channelUID, val);
            }
        } else {
            logger.warn("Channel {} update failed.", channelUID);
        }
    }

    /**
     * Update resource value to IHC controller.
     */
    private boolean updateResource(WSResourceValue value) throws IhcExecption {
        boolean result = false;
        try {
            result = ihc.resourceUpdate(value);
        } catch (IhcExecption e) {
            logger.warn("Value could not be updated - retrying one time: {}", e.getMessage());
            result = ihc.resourceUpdate(value);
        }
        return result;
    }

    @Override
    public void channelLinked(ChannelUID channelUID) {
        logger.debug("channelLinked: {}", channelUID);

        switch (channelUID.getId()) {
            case CHANNEL_CONTROLLER_STATE:
                updateControllerStateChannel();
                break;

            case CHANNEL_CONTROLLER_SW_VERSION:
            case CHANNEL_CONTROLLER_HW_VERSION:
            case CHANNEL_CONTROLLER_UPTIME:
            case CHANNEL_CONTROLLER_TIME:
                updateControllerInformationChannels();
                break;

            default:
                try {
                    ChannelParams params = new ChannelParams(thing.getChannel(channelUID.getId()));
                    if (params.getResourceId() != null) {
                        synchronized (linkedResourceIds) {
                            linkedResourceIds.add(params.getResourceId());
                        }
                        updateNotificationsRequestReminder();
                    }
                } catch (IllegalArgumentException e) {
                    logger.warn("Can't find resource id, reason {}", e.getMessage());
                }
        }
    }

    @Override
    public void channelUnlinked(ChannelUID channelUID) {
        logger.debug("channelUnlinked: {}", channelUID);

        switch (channelUID.getId()) {
            case CHANNEL_CONTROLLER_STATE:
            case CHANNEL_CONTROLLER_SW_VERSION:
            case CHANNEL_CONTROLLER_HW_VERSION:
            case CHANNEL_CONTROLLER_UPTIME:
            case CHANNEL_CONTROLLER_TIME:
                break;

            default:
                try {
                    ChannelParams params = new ChannelParams(thing.getChannel(channelUID.getId()));
                    if (params.getResourceId() != null) {
                        synchronized (linkedResourceIds) {
                            linkedResourceIds.removeIf(c -> c.equals(params.getResourceId()));
                        }
                        updateNotificationsRequestReminder();
                    }
                } catch (IllegalArgumentException e) {
                    logger.warn("Can't find resource id, reason {}", e.getMessage());
                }
        }
    }

    /**
     * Initialize IHC client and open connection to IHC / ELKO LS controller.
     *
     */
    private void connect() throws IhcExecption {
        try {
            setConnectingState(true);
            logger.debug("Connecting to IHC / ELKO LS controller [IP='{}' Username='{}' Password='{}'].",
                    new Object[] { conf.ip, conf.username, "******" });
            ihc = new IhcClient(conf.ip, conf.username, conf.password, conf.timeout);
            ihc.openConnection();
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE,
                    "Initializing communication to the IHC / ELKO controller");
            loadProject();
            createChannels();
            updateControllerStateChannel();
            updateControllerInformationChannels();
            ihc.addEventListener(this);
            ihc.startControllerEventListeners();
            setValueNotificationRequest(true);
            startRFPolling();
        } finally {
            setConnectingState(false);
        }
    }

    private void loadProject() throws IhcExecption {
        boolean loadProject = false;
        String fileName = String.format(LOCAL_IHC_PROJECT_FILE_NAME_TEMPLATE, thing.getUID().getId());
        String filePath = getFilePathInUserDataFolder(fileName);

        if (conf.loadProjectFile && projectFile == null) {
            projectFile = ProjectFileUtils.readProjectFileFromFile(filePath);
            if (projectFile == null) {
                loadProject = true;
            }
        } else if (conf.loadProjectFile
                && !ProjectFileUtils.projectEqualsToControllerProject(projectFile, ihc.getProjectInfo())) {
            logger.debug("Local project file is not same as in the controller, reload project file from controller!");
            loadProject = true;
        }

        if (loadProject == true) {
            logger.debug("Loading IHC /ELKO LS project file from controller...");
            byte[] data = ihc.loadProjectFileFromControllerAsByteArray();
            logger.debug("Saving project file to local file '{}'", filePath);
            ProjectFileUtils.saveProjectFile(filePath, data);
            projectFile = ProjectFileUtils.converteBytesToDocument(data);
        }

        enumDictionary = new EnumDictionary(ProjectFileUtils.parseEnums(projectFile));
    }

    private void createChannels() {
        if (conf.loadProjectFile && conf.createChannelsAutomatically) {
            logger.debug("Creating channels");
            List<Channel> thingChannels = new ArrayList<>();
            thingChannels.addAll(getThing().getChannels());
            ChannelUtils.addControllerChannels(getThing(), thingChannels);
            try {
                ChannelUtils.addRFDeviceChannels(getThing(), ihc.getDetectedRFDevices(), thingChannels);
            } catch (IhcExecption e) {
                logger.debug("Error occured when fetching RF device information, reason: {} ", e.getMessage());
            }
            ChannelUtils.addChannelsFromProjectFile(getThing(), projectFile, thingChannels);
            printChannels(thingChannels);
            updateThing(editThing().withChannels(thingChannels).build());
        } else {
            logger.debug("Automatic channel creation disabled");
        }
    }

    private void printChannels(List<Channel> thingChannels) {
        if (logger.isDebugEnabled()) {

            thingChannels.forEach(channel -> {
                String resourceId;
                try {
                    Object id = channel.getConfiguration().get(PARAM_RESOURCE_ID);
                    resourceId = id != null ? "0x" + Integer.toHexString(((BigDecimal) id).intValue()) : "";
                } catch (IllegalArgumentException e) {
                    resourceId = "";
                }

                logger.debug("Channel: {}",
                        String.format("%-50s | %-10s | %s", channel.getUID(), resourceId, channel.getLabel()));
            });
        }
    }

    private void startRFPolling() {
        if (pollingJobRf == null || pollingJobRf.isCancelled()) {
            logger.debug("Start RF device refresh task, interval={}sec", 60);
            pollingJobRf = scheduler.scheduleWithFixedDelay(pollingRunnableRF, 10, 60, TimeUnit.SECONDS);
        }
    }

    /**
     * Disconnect connection to IHC / ELKO LS controller.
     *
     */
    private void disconnect() {
        if (pollingJobRf != null && !pollingJobRf.isCancelled()) {
            pollingJobRf.cancel(true);
            pollingJobRf = null;
        }
        if (ihc != null) {
            try {
                ihc.removeEventListener(this);
                ihc.closeConnection();
                ihc = null;
            } catch (IhcExecption e) {
                logger.warn("Couldn't close connection to IHC controller", e);
            }
        }
    }

    @Override
    public void errorOccured(IhcExecption e) {
        logger.warn("Error occurred on communication to IHC controller: {}", e.getMessage());
        logger.debug("Reconnection request");
        setReconnectRequest(true);
    }

    @Override
    public void statusUpdateReceived(WSControllerState newState) {
        logger.debug("Controller state: {}", newState.getState());

        if (!controllerState.equals(newState.getState())) {
            logger.debug("Controller state change detected ({} -> {})", controllerState, newState.getState());

            switch (newState.getState()) {
                case IhcClient.CONTROLLER_STATE_INITIALIZE:
                    logger.info("Controller state changed to initializing state, waiting for ready state");
                    updateState(new ChannelUID(getThing().getUID(), CHANNEL_CONTROLLER_STATE),
                            new StringType("initialize"));
                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE,
                            "Controller is in initializing state");
                    break;
                case IhcClient.CONTROLLER_STATE_READY:
                    logger.info("Controller state changed to ready state");
                    updateState(new ChannelUID(getThing().getUID(), CHANNEL_CONTROLLER_STATE), new StringType("ready"));
                    updateStatus(ThingStatus.ONLINE);
                    break;
                default:
            }

            if (controllerState.equals(IhcClient.CONTROLLER_STATE_INITIALIZE)
                    && newState.getState().equals(IhcClient.CONTROLLER_STATE_READY)) {

                logger.debug("Reconnection request");
                projectFile = null;
                setReconnectRequest(true);
            }
        }

        controllerState = newState.getState();
    }

    @Override
    public void resourceValueUpdateReceived(WSResourceValue value) {
        logger.debug("resourceValueUpdateReceived: {}", value);

        thing.getChannels().forEach(channel -> {
            ChannelParams params = new ChannelParams(channel);
            if (params.getResourceId() != null && params.getResourceId().intValue() == value.getResourceID()) {
                updateChannelState(channel, params, value);
            }
        });

        checkTriggers(value);
    }

    private void updateChannelState(Channel channel, ChannelParams params, WSResourceValue value) {
        if (params.isDirectionWriteOnly()) {
            logger.debug("Write only channel, skip update to {}", channel.getUID());
        } else {
            if (params.getChannelTypeId() != null) {
                switch (params.getChannelTypeId()) {
                    case CHANNEL_TYPE_PUSH_BUTTON_TRIGGER:
                        break;

                    default:
                        try {
                            logger.debug("Channel params: {}", params);
                            Converter<WSResourceValue, Type> converter = ConverterFactory.getInstance()
                                    .getConverter(value.getClass(), channel.getAcceptedItemType());
                            State state = (State) converter.convertFromResourceValue(value,
                                    new ConverterAdditionalInfo(null, params.isInverted()));
                            updateState(channel.getUID(), state);
                        } catch (NumberFormatException e) {
                            logger.debug("Can't convert resource value '{}' to item type {}", value,
                                    channel.getAcceptedItemType());
                        }
                }
            }
        }
    }

    private void checkTriggers(WSResourceValue value) {
        if (value instanceof WSBooleanValue) {
            if (((WSBooleanValue) value).isValue() == false) {
                LocalDateTime lastUpdateTime = lastUpdate.get(value.getResourceID());
                if (lastUpdateTime != null) {
                    Duration duration = Duration.between(lastUpdateTime, LocalDateTime.now());
                    logger.debug("Time between uddates: {}", duration);
                    updateTriggers(value.getResourceID(), duration);
                }
            } else {
                lastUpdate.put(value.getResourceID(), LocalDateTime.now());
            }
        }
    }

    private void updateTriggers(int resourceId, Duration duration) {
        thing.getChannels().forEach(channel -> {
            ChannelParams params = new ChannelParams(channel);
            if (params.getResourceId() != null && params.getResourceId().intValue() == resourceId) {
                if (params.getChannelTypeId() != null) {
                    switch (params.getChannelTypeId()) {
                        case CHANNEL_TYPE_PUSH_BUTTON_TRIGGER:
                            ButtonPressDurationDetector button = new ButtonPressDurationDetector(duration,
                                    params.getShortPressMaxTime(), params.getLongPressMaxTime(),
                                    params.getExtraLongPressMaxTime());
                            logger.debug("resourceId={}, ButtonPressDurationDetector={}", resourceId, button);
                            if (button.isShortPress()) {
                                triggerChannel(channel.getUID().getId(), EVENT_SHORT_PRESS);
                            } else if (button.isLongPress()) {
                                triggerChannel(channel.getUID().getId(), EVENT_LONG_PRESS);
                            } else if (button.isExtraLongPress()) {
                                triggerChannel(channel.getUID().getId(), EVENT_EXTRA_LONG_PRESS);
                            }
                            break;
                    }
                }
            }
        });
    }

    private void updateRfDeviceStates() {
        if (ihc != null) {
            if (ihc.getConnectionState() != ConnectionState.CONNECTED) {
                logger.debug("Controller is connecting, abort subscribe");
                return;
            }

            logger.debug("Update RF device data");
            try {
                List<WSRFDevice> devs = ihc.getDetectedRFDevices();
                logger.debug("RF data: {}", devs);

                devs.forEach(dev -> {
                    thing.getChannels().forEach(channel -> {
                        ChannelParams params = new ChannelParams(channel);
                        if (params.getSerialNumber() != null
                                && params.getSerialNumber().longValue() == dev.getSerialNumber()) {
                            String channelId = channel.getUID().getId();
                            if (params.getChannelTypeId() != null) {
                                switch (params.getChannelTypeId()) {
                                    case CHANNEL_TYPE_RF_LOW_BATTERY:
                                        updateState(channelId,
                                                dev.getBatteryLevel() == 1 ? OnOffType.OFF : OnOffType.ON);
                                        break;
                                    case CHANNEL_TYPE_RF_SIGNAL_STRENGTH:
                                        int signalLevel = new SignalLevelConverter(dev.getSignalStrength())
                                                .getSystemWideSignalLevel();
                                        updateState(channelId, new StringType(String.valueOf(signalLevel)));
                                        break;
                                }
                            }
                        }
                    });
                });
            } catch (IhcExecption e) {
                logger.debug("Error occured when fetching RF device information, reason: {} ", e.getMessage());
                return;
            }
        }
    }

    private void reconnectCheck() {
        if (ihc == null || isReconnectRequestActivated()) {
            try {
                if (ihc != null) {
                    disconnect();
                }
                connect();
                updateStatus(ThingStatus.ONLINE);
                setReconnectRequest(false);
            } catch (IhcExecption e) {
                logger.debug("Can't open connection to controller", e.getMessage());
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
                setReconnectRequest(true);
                return;
            }
        }

        if (isValueNotificationRequestActivated() && !isConnecting()) {
            try {
                enableResourceValueNotifications();
            } catch (IhcExecption e) {
                logger.warn("Can't enable resource value notifications from controller. ", e);
            }
        }
    }

    /**
     * Order resource value notifications from IHC controller.
     */
    private void enableResourceValueNotifications() throws IhcExecption {
        logger.debug("Subscribe resource runtime value notifications");

        if (ihc != null) {
            if (ihc.getConnectionState() != ConnectionState.CONNECTED) {
                logger.debug("Controller is connecting, abort subscribe");
                return;
            }
            Set<Integer> resourceIds = ChannelUtils.getAllTriggerChannelsResourceIds(getThing());
            logger.debug("Enable runtime notfications for {} trigger(s)", resourceIds.size());
            logger.debug("Enable runtime notfications for {} channel(s)", linkedResourceIds.size());
            resourceIds.addAll(linkedResourceIds);
            if (resourceIds.size() > 0) {
                logger.debug("Enable runtime notfications for {} resources", resourceIds.size());
                try {
                    ihc.enableRuntimeValueNotifications(resourceIds);
                } catch (IhcExecption e) {
                    logger.debug("Reconnection request");
                    setReconnectRequest(true);
                }
            }
        } else {
            logger.warn("Controller is not initialized!");
            logger.debug("Reconnection request");
            setReconnectRequest(true);
        }

        setValueNotificationRequest(false);
    }

    private synchronized void updateNotificationsRequestReminder() {
        if (reminder != null) {
            reminder.cancel();
            reminder = null;
        }

        reminder = new NotificationsRequestReminder(NOTIFICATIONS_REORDER_WAIT_TIME);
    }

    /**
     * Used to slow down resource value notification ordering process. All
     * resource values need to be ordered by one request from the controller,
     * therefore wait that all channels are linked.
     */
    private class NotificationsRequestReminder {
        Timer timer;

        public NotificationsRequestReminder(int milliseconds) {
            timer = new Timer();
            timer.schedule(new RemindTask(), milliseconds);
        }

        public void cancel() {
            timer.cancel();
        }

        class RemindTask extends TimerTask {

            @Override
            public void run() {
                logger.debug("Timer: Delayed resource value notifications request is now enabled");
                setValueNotificationRequest(true);
                timer.cancel();
            }
        }
    }
}
