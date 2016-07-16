/**
 * Copyright 2016 Jethro Carr. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package com.jethrocarr.howalarming.gcmserver;

import com.google.gson.JsonObject;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Data model for push messages to mobile devices via GCM
 */
public class PushMessage {

    public String priority;
    public Map<String,String> data;
    public Map<String,String> notification;

    public PushMessage() {
        // Data for the actual apps (iOS + Android), same format as the documented HowAlarming beanstalk queue.
        data = new ConcurrentHashMap<String, String>();

        // Fields used for APNS messages (iOS) to determine the information for the notification centre.
        notification = new ConcurrentHashMap<String, String>();

        // We want to ensure APNS always give us priority pushes for alarm events, otherwise
        // alarm events can be delayed.
        priority = "high";
    }
/*
    public isValid() {
      // TODO: Need to write a validator for all the fields that are required.
    }
  */


    public void alarmStatus() {
        // Package the current state of the alarm system into a Push Message

        data.put("raw", "HOWALARMING");
        data.put("code", "HOWALARMING");
        data.put("type", "disarmed");
        data.put("message", "HOWALARMING");

        Long timestamp = System.currentTimeMillis() / 1000L;
        data.put("timestamp", timestamp.toString());

        // Tell APNS/GCM that this should be a silent background notification
        //notification.put("content-available", "1");
    }

    public void fromBeanstalk(JsonObject jData) {
        // Take a JSON message from beanstalk and package it into a PushMessage.

        data.put("raw", jData.get("raw").getAsString());
        data.put("code", jData.get("code").getAsString());
        data.put("type", jData.get("type").getAsString());
        data.put("message", jData.get("message").getAsString());
        data.put("timestamp", jData.get("timestamp").getAsString());

        notification.put("badge", "0");
        notification.put("sound", "default");
        notification.put("title", "HowAlarming Event " + data.get("type"));
        notification.put("body", data.get("message"));

    }

}
