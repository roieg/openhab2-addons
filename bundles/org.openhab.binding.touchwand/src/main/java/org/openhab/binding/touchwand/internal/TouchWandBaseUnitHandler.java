/**
 * Copyright (c) 2010-2020 Contributors to the openHAB project
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
import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandler;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.RefreshType;
import org.openhab.binding.touchwand.internal.dto.TouchWandUnitData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

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

    protected final Logger logger = LoggerFactory.getLogger(TouchWandBaseUnitHandler.class);
    public static final Set<ThingTypeUID> SUPPORTED_THING_TYPES = new HashSet<>(
            Arrays.asList(THING_TYPE_SHUTTER, THING_TYPE_SWITCH, THING_TYPE_WALLCONTROLLER, THING_TYPE_DIMMER));

    @NonNullByDefault({})
    protected String unitId;

    @NonNullByDefault({})
    protected TouchWandBridgeHandler bridgeHandler;

    public TouchWandBaseUnitHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (command instanceof RefreshType) {
            // updateTouchWandUnitState(getUnitState(unitId));
        } else {
            touchWandUnitHandleCommand(command);
        }
    }

    @Override
    public void dispose() {
        if (bridgeHandler != null) {
            bridgeHandler.unregisterUpdateListener(this);
        }
    }

    @Override
    public void initialize() {
        Bridge bridge = getBridge();
        if (bridge == null || !(bridge.getHandler() instanceof TouchWandBridgeHandler)) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE);
            logger.warn("Trying to initialize {} without a bridge", getThing().getUID());
            return;
        }

        bridgeHandler = (TouchWandBridgeHandler) bridge.getHandler();

        unitId = getThing().getUID().getId(); // TouchWand unit id

        bridgeHandler.registerUpdateListener(this);

        updateStatus(ThingStatus.UNKNOWN);
        scheduler.execute(() -> {
            boolean thingReachable = false;
            String response = bridgeHandler.touchWandClient.cmdGetUnitById(unitId);
            thingReachable = !response.isEmpty();
            if (thingReachable) {
                updateStatus(ThingStatus.ONLINE);
            } else {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR);
            }
        });
    }

    private int getUnitState(String unitId) {
        int status = 0;

        if (bridgeHandler == null) {
            return status;
        }

        String response = bridgeHandler.touchWandClient.cmdGetUnitById(unitId);
        if (!response.isEmpty()) {
            return status;
        }

        JsonParser jsonParser = new JsonParser();

        try {
            JsonObject unitObj = jsonParser.parse(response).getAsJsonObject();
            status = unitObj.get("currStatus").getAsInt();
            if (!this.getThing().getStatusInfo().getStatus().equals(ThingStatus.ONLINE)) {
                updateStatus(ThingStatus.ONLINE);
            }
        } catch (JsonParseException | IllegalStateException e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, "Could not parse cmdGetUnitById");
            logger.warn("Could not parse cmdGetUnitById response for unit id {}  label {}", unitId,
                    getThing().getLabel());
        }
        return status;
    }

    abstract void touchWandUnitHandleCommand(Command command);

    abstract void updateTouchWandUnitState(TouchWandUnitData unitData);

    @Override
    public void onItemStatusUpdate(TouchWandUnitData unitData) {
        if (unitData.getStatus().equals("ALIVE")) {
            updateStatus(ThingStatus.ONLINE);
        } else {
            // updateStatus(ThingStatus.OFFLINE); // comment - OFFLINE status is not accurate at the moment
        }
        updateTouchWandUnitState(unitData);
    }

    @Override
    public String getId() {
        return unitId;
    }
}
