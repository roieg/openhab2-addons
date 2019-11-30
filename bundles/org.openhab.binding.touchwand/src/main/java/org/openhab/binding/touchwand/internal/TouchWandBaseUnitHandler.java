/**
 * Copyright (c) 2010-2019 Contributors to the openHAB project
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
package org.openhab.binding.touchwand.internal;

import static org.openhab.binding.touchwand.internal.TouchWandBindingConstants.*;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandler;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.RefreshType;
import org.openhab.binding.touchwand.internal.data.TouchWandUnitData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

/**
 * The {@link TouchWandBaseUnitHandler} is responsible for handling commands and status updates
 * for TouchWand units. This is an abstract class , units should implement the specific command
 * handling and status updates.
 *
 * @author Roie Geron - Initial contribution
 *
 */
@NonNullByDefault
public abstract class TouchWandBaseUnitHandler extends BaseThingHandler implements TouchWandUnitUpdateListener {

    public TouchWandBaseUnitHandler(Thing thing) {
        super(thing);
    }

    @NonNullByDefault({})
    protected String unitId;
    private final Logger logger = LoggerFactory.getLogger(TouchWandBaseUnitHandler.class);
    private final static int INITIAL_UPDATE_TIME = 10;
    private @Nullable ScheduledFuture<?> pollingJob;

    @NonNullByDefault({})
    protected TouchWandBridgeHandler bridgeHandler;
    public static final Set<ThingTypeUID> SUPPORTED_THING_TYPES = new HashSet<>(
            Arrays.asList(THING_TYPE_SHUTTER, THING_TYPE_SWITCH));

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (command instanceof RefreshType) {
            updateTouchWandUnitState(getUnitState(unitId));
        } else {
            touhWandUnitHandleCommand(command);
        }
    }

    @Override
    public void dispose() {
        logger.trace("Handler disposed.");
        if (pollingJob != null) {
            pollingJob.cancel(true);
        }
        if (bridgeHandler != null) {
            bridgeHandler.unregisterUpdateListener(this);
        }
    }

    @Override
    public void initialize() {

        ThingStatus bridgeStatus;

        logger.trace("Initializing TocuhWand Unit handler");

        Bridge bridge = getBridge();

        if (bridge == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR);
            logger.warn("Trying to initialize {} without a bridge", getThing().getUID());
            return;
        } else {
            bridgeHandler = (TouchWandBridgeHandler) bridge.getHandler();
            bridgeStatus = bridge.getStatus();
        }

        updateStatus(ThingStatus.UNKNOWN);

        if (!bridgeStatus.equals(ThingStatus.ONLINE)) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE);
        }

        logger.trace("initializeThing Thing {} Bridge status {}", getThing().getUID(), bridgeStatus);

        Thing thing = getThing();
        Map<String, String> properties = thing.getProperties();
        unitId = properties.get("id");
        bridgeHandler.registerUpdateListener(this);

        scheduler.execute(() -> {
            boolean thingReachable = false;
            String response = bridgeHandler.touchWandClient.cmdGetUnitById(unitId);
            thingReachable = !(response == null);
            if (thingReachable) {
                updateStatus(ThingStatus.ONLINE);
                // int statusRefreshRate = bridgeHandler.getStatusRefreshTime();
                // pollingJob = scheduler.scheduleWithFixedDelay(runnable, INITIAL_UPDATE_TIME, statusRefreshRate,
                // TimeUnit.SECONDS);
            } else {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR);
            }
        });

        logger.trace("Finished initializing!");
    }

    private int getUnitState(String unitId) {

        int status = 0;

        if (bridgeHandler == null) {
            return status;
        }

        String response = bridgeHandler.touchWandClient.cmdGetUnitById(unitId);
        if (response == null) {
            return status;
        }

        JsonParser jsonParser = new JsonParser();

        try {
            JsonObject unitObj = jsonParser.parse(response).getAsJsonObject();
            status = unitObj.get("currStatus").getAsInt();
            if (!this.getThing().getStatusInfo().getStatus().equals(ThingStatus.ONLINE)) {
                updateStatus(ThingStatus.ONLINE);
            }
        } catch (JsonSyntaxException | IllegalStateException e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR);
            logger.warn("Could not parse cmdGetUnitById response {}", getThing().getLabel());
        }
        return status;
    }

    abstract void touhWandUnitHandleCommand(Command command);

    abstract void updateTouchWandUnitState(int status);

    Runnable runnable = new Runnable() {
        @Override
        public void run() {
            updateTouchWandUnitState(getUnitState(unitId));
        }
    };

    @Override
    public void onItemStatusUpdate(TouchWandUnitData unitData) {
        logger.debug("Received Status update , I am unitData {} , update unit {} status {}", unitData.getId(), unitId,
                unitData.getCurrStatus());
        updateTouchWandUnitState(unitData.getCurrStatus());
    }

    @Override
    public String getId() {
        return unitId;
    }

}