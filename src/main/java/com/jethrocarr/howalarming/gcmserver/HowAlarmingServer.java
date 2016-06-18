/**
 * Copyright 2015 Google Inc. All Rights Reserved.
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

import com.google.gson.*;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import org.jivesoftware.smack.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * HowAlarmingServer provides the logic for registering devices and providing communication between the server
 * and the client devices/apps.
 */
public class HowAlarmingServer {

  // FriendlyGcmServer defines onMessage to handle incoming friendly ping messages.
  private class FriendlyGcmServer extends GcmServer {

    public FriendlyGcmServer (String apiKey, String senderId, String serviceName) {
      super(apiKey, senderId, serviceName);
    }

    @Override
    public void onMessage(String from, JsonObject jData) {
      /**
       * We only support a few of the common commands to all alarm models here.
       */

      if (jData.has("registration_token")) {
        String registration_token = jData.get("registration_token").getAsString();
        logger.info("Message sender: "+ registration_token);

        if (!registeredClients.contains(registration_token)) {
          registerNewClient(registration_token);
        }
      }

      if (jData.has("command")) {
        String command = jData.get("command").getAsString();

        logger.info("Command \""+ command +"\" received from device.");

        switch (command) {
          case "status":
          case "arm":
          case "disarm":
          case "fire":
          case "medical":
          case "police":
            // Supported simple commands in HowAlarming.

            // TODO: Feed into beanstalk queue.

            // TODO: test by sending message
            messageAllClients();
          break;

          default:
            logger.warning("Command "+ command +" is not a support simple command type, unable to action message");
        }


      } else {
        logger.info("Unexpected message received from GCM, ignoring.");
      }
    }
  }

  private static final Logger logger = Logger.getLogger("HowAlarmingServer");

  // Creds
  private static final String SERVER_API_KEY = System.getenv("GCM_API_KEY");
  private static final String SENDER_ID = System.getenv("GCM_SENDER_ID");


  // Other
  public static final String SERVICE_NAME = "HowAlarming GCM Server";

  // Store registered clients for life of the application. This is populated fresh after the server
  // and clients are launched consecutively.
  private List<String> registeredClients;

  // Listener responsible for handling incoming registrations and pings.
  private FriendlyGcmServer friendlyGcmServer;

  // Gson helper to assist with going to and from JSON and Client.
  private Gson gson;


  public HowAlarmingServer(String apiKey, String senderId) {

    registeredClients = new ArrayList<String>();

    gson = new GsonBuilder().create();

    friendlyGcmServer = new FriendlyGcmServer(apiKey, senderId, SERVICE_NAME);
  }


  /**
   * Add a new client to the client list.
   *
   * @param registrationToken String with GCM token ID of client.
   */
  private void registerNewClient(String registrationToken) {
    if (!registeredClients.contains(registrationToken)) {
      logger.info("Registered new client "+ registrationToken);
      registeredClients.add(registrationToken);
    }
  }


  /**
   * Define the format of push messages to the device
   */

  public class pushMessage {
    public String priority;
    public Map<String,String> data;
    public Map<String,String> notification;

    public pushMessage() {
      // Data for the actual apps (iOS + Android), same format as the documented HowAlarming beanstalk queue.
      data = new ConcurrentHashMap<String, String>();

      // Fields used for APNS messages (iOS) to determine the information for the notification centre.
      notification = new ConcurrentHashMap<String, String>();

      // We want to ensure APNS always give us priority pushes for alarm events, otherwise
      // alarm events can be delayed.
      priority = "high";
    }
  }

  /**
   * Deliver a message to all registered clients (basically broadcast, we don't need to care about 1-1 messaging)
   */
  private void messageAllClients() {

    logger.info("Dispatching broadcast message to all registered clients");

    pushMessage myPushMessage = new pushMessage();

    myPushMessage.data.put("raw", "123abc");
    myPushMessage.data.put("message", "test message");
    myPushMessage.data.put("type", "alarm");
    myPushMessage.data.put("code", "123");
    myPushMessage.data.put("timestamp", (String.valueOf((System.currentTimeMillis() / 1000L))));

    myPushMessage.notification.put("badge", "0");
    myPushMessage.notification.put("sound", "default");
    myPushMessage.notification.put("title", "HowAlarming Event");
    myPushMessage.notification.put("body", "Test Message");

    String messageString = gson.toJson(myPushMessage);
    JsonObject jData = new JsonParser().parse(messageString).getAsJsonObject();

    for (String clientToken : registeredClients) {
      friendlyGcmServer.send(clientToken, jData);
    }
  }

  public static void main(String[] args) {

    // Initialize HowAlarmingServer with appropriate API Key and SenderID.
    new HowAlarmingServer(SERVER_API_KEY, SENDER_ID);

    // Keep main thread alive.
    try {
      CountDownLatch latch = new CountDownLatch(1);
      latch.await();
    } catch (InterruptedException e) {
      logger.log(Level.SEVERE, "An error occurred while latch was waiting.", e);
    }
  }
}
