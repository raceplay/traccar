/*
 * Copyright 2017 - 2019 Anton Tananaev (anton@traccar.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.traccar.protocol;

import io.netty.channel.Channel;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traccar.BaseHttpProtocolDecoder;
import org.traccar.DeviceSession;
import org.traccar.Protocol;
import org.traccar.helper.DateUtil;
import org.traccar.model.Position;

import javax.json.*;
import java.io.StringReader;
import java.net.SocketAddress;
import java.net.URLDecoder;
import java.util.Date;

public class SpotProtocolDecoder extends BaseHttpProtocolDecoder {

    private static final Logger LOGGER = LoggerFactory.getLogger(SpotProtocolDecoder.class);

    public SpotProtocolDecoder(Protocol protocol) {
        super(protocol);
    }

    @Override
    protected Object decode(
            Channel channel, SocketAddress remoteAddress, Object msg) throws Exception {
        JsonObject json;
        if (msg instanceof String) {
            String content = (String) msg;
            if (!content.startsWith("{")) {
                content = URLDecoder.decode(content.split("=")[0], "UTF-8");
            }
            json = Json.createReader(new StringReader(content)).readObject();
        } else if (msg instanceof JsonObject) {
            json = (JsonObject) msg;
        } else {
            LOGGER.warn("Unknown message type: " + msg.getClass() + " : " + msg.toString());
            sendResponse(channel, HttpResponseStatus.BAD_REQUEST);
            return null;
        }

        String deviceId;
        if (jsonContains(json, "esn")) {
            deviceId = json.getString("esn");
        } else {
            LOGGER.warn("No esn provided");
            sendResponse(channel, HttpResponseStatus.BAD_REQUEST);
            return null;
        }

        DeviceSession deviceSession = getDeviceSession(channel, remoteAddress, deviceId);
        if (deviceSession == null) {
            sendResponse(channel, HttpResponseStatus.BAD_REQUEST);
            return null;
        }

        Position position = new Position(getProtocolName());
        position.setDeviceId(deviceSession.getDeviceId());

        if (jsonContains(json, "timestamp")) {
            position.setTime(DateUtil.parseDate(json.getString("timestamp")));
        } else {
            position.setTime(new Date());
        }

        if (jsonContains(json, "latitude")) {
            position.setLatitude(getJsonDouble(json, "latitude"));
        }
        if (jsonContains(json, "longitude")) {
            position.setLongitude(getJsonDouble(json, "longitude"));
        }
        if(position.getLatitude() != 0 && position.getLongitude() != 0) {
            position.setValid(true);
        }

        if (jsonContains(json, "messageType")) {
            position.set(Position.KEY_EVENT, json.getString("messageType"));
        }

        sendResponse(channel, HttpResponseStatus.OK);
        return position;
    }

    private boolean jsonContains(JsonObject json, String key) {
        if (json.containsKey(key)) {
            JsonValue value = json.get(key);
            if (value.getValueType() == JsonValue.ValueType.STRING) {
                return !((JsonString) value).getString().equals("null");
            } else {
                return true;
            }
        }
        return false;
    }

    private double getJsonDouble(JsonObject json, String key) {
        JsonValue value = json.get(key);
        if (value != null) {
            if (value.getValueType() == JsonValue.ValueType.NUMBER) {
                return ((JsonNumber) value).doubleValue();
            } else if (value.getValueType() == JsonValue.ValueType.STRING) {
                return Double.parseDouble(((JsonString) value).getString());
            }
        }
        return 0;
    }
}
