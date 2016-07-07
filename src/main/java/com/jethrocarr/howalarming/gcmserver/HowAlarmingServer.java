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
import com.dinstone.beanstalkc.*;

import java.util.ArrayList;
import java.util.Map;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * HowAlarmingServer provides the logic for registering devices and providing communication between the server
 * and the client devices/apps.
 */
public class HowAlarmingServer {


  // MARK: GcmServer

  /**
   * We define our own specific GcmServer in order to override onMessage with specific logic for handling the messages
   * we receive from the mobile apps via the GCM network.
   */
  private class HowAlarmingGcmServer extends GcmServer {

    public HowAlarmingGcmServer (String apiKey, String senderId, String serviceName) {
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
            // Supported simple commands in HowAlarming. We feed these into the beanstalk queue(s)
            beanstalkClient.beanstalkPost(command);
          break;

          default:
            logger.warning("Command "+ command +" is not a support simple command type, unable to action message");
        }


      } else {
        logger.info("Unexpected message received from GCM, ignoring.");
      }
    }
  }


  // MARK: BeanstalkClient

  /*
    BeanstalkClient defines a class for exchanging messages to/from Beanstalk
   */
  public class BeanstalkClient {

    private Configuration beanstalkConfig;
    private BeanstalkClientFactory beanstalkFactory;

    public BeanstalkClient() {
      // Connect to beanstalk queue
      beanstalkConfig = new Configuration();
      beanstalkConfig.setServiceHost(BEANSTALK_HOST);
      beanstalkConfig.setServicePort(Integer.parseInt(BEANSTALK_PORT));

      beanstalkFactory = new BeanstalkClientFactory(beanstalkConfig);

      // Launch the tube listener in a dedicated thread.
      Thread beanstalkClientThread = new Thread(new BeanstalkListener());
      beanstalkClientThread.setName("Beanstalk Queue Reader");
      beanstalkClientThread.start();
    }

    /**
     * The beanstalkPost method is called via the GCM server when a new (valid) command is received from a mobile
     * device via GCM. It takes the message and pops it onto the queue(s) for the alarm to action.
     */
    public void beanstalkPost(String message) {
      logger.info("Posting message to beanstalk:" + message);

      boolean success = false;
      while (!success) {

        try {
          // TODO: hard coded for testing, agwghgi
          JobProducer producer = beanstalkFactory.createJobProducer(BEANSTALK_TUBES_COMMANDS);
          producer.putJob(0, 0, 300, message.getBytes());

          // Sad that Java doesn't have a proper re-try catch and that we have to resort to this :-(
          success = true;
        } catch (ConnectionException e) {
          logger.log(Level.SEVERE, "Unable to establish a connection to Beanstalk, retrying in 30 seconds", e);

          // 30 second sleep between retries to avoid cpu going crazy ;-)
          try {
            Thread.sleep(30000);
          } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
          }
        }
      }

    }





    /**
     * The listener runs long polls (60 seconds) in a loop waiting for any messages from the queue. Upon receiving a
     * message, it broadcasts it to all registered clients via the GCM network.
     */
    public class BeanstalkListener implements Runnable {

      public void run() {

        // TODO: hardcoded
        JobConsumer consumer = beanstalkFactory.createJobConsumer("alert_gcm");

        while (true) {

          // Poll for new messages to process
          try {
            Job job = consumer.reserveJob(60);

            if (job != null) {
              // Obtain the message and push to all clients
              // TODO: I think we need to filter accepted messages here?

              // TODO: This could almost certainly be re-worked into less mess
              String message = new String(job.getData());
              JsonObject messageJson = new JsonParser().parse(message).getAsJsonObject();

              PushMessage myPushMessage = new PushMessage();
              myPushMessage.fromBeanstalk(messageJson);

              messageAllClients(myPushMessage);

              // Delete old message
              consumer.deleteJob(job.getId());
            }
          } catch (ConnectionException e) {
            logger.log(Level.SEVERE, "Unable to establish a connection to Beanstalk, retrying in 30 seconds", e);

            // 30 second sleep between retries to avoid cpu going crazy ;-)
            try {
              Thread.sleep(30000);
            } catch (InterruptedException ex) {
              Thread.currentThread().interrupt();
            }
          }

        }

        // No need to close consumer, we listen until the app is terminated.
      }
    }
  }



  // MARK: Properties


  private static final Logger logger = Logger.getLogger("HowAlarmingServer");
  public static final String SERVICE_NAME = "HowAlarming GCM Server";

  // Creds
  private static final String SERVER_API_KEY = System.getenv("GCM_API_KEY");
  private static final String SENDER_ID = System.getenv("GCM_SENDER_ID");

  // Beanstalk Queue
  private static String BEANSTALK_HOST            = System.getenv("BEANSTALK_HOST");
  private static String BEANSTALK_PORT            = System.getenv("BEANSTALK_PORT");
  private static String BEANSTALK_TUBES_EVENTS    = System.getenv("BEANSTALK_TUBES_EVENTS");
  private static String BEANSTALK_TUBES_COMMANDS  = System.getenv("BEANSTALK_TUBES_COMMANDS");

  // Store registered clients for life of the application. This is populated fresh after the server
  // and clients are launched consecutively.
  private List<String> registeredClients;

  // Listener responsible for handling incoming registrations and pings.
  private HowAlarmingGcmServer HowAlarmingGcmServer;

  // Beanstalk Client
  private BeanstalkClient beanstalkClient;

  // Gson helper to assist with going to and from JSON and Client.
  private Gson gson;


  // MARK: HowAlarming Server


  public HowAlarmingServer(String apiKey, String senderId) {

    // Validate configuration
    if (BEANSTALK_HOST == null) {
      BEANSTALK_HOST="127.0.0.1";
    }

    if (BEANSTALK_PORT == null) {
      BEANSTALK_PORT="11300";
    }

    if (BEANSTALK_TUBES_EVENTS == null) {
      BEANSTALK_TUBES_EVENTS="alert_gcm";
    }

    if (BEANSTALK_TUBES_COMMANDS == null) {
      BEANSTALK_TUBES_COMMANDS="commands";
    }

    registeredClients = new ArrayList<String>();
    gson = new GsonBuilder().create();

    beanstalkClient = new BeanstalkClient();
    HowAlarmingGcmServer = new HowAlarmingGcmServer(apiKey, senderId, SERVICE_NAME);
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

    public void fromBeanstalk(JsonObject jData) {
      // Take a JSON message from beantalk and package it into a PushMessage.

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

  /**
   * Deliver a message to all registered clients (basically broadcast, we don't need to care about 1-1 messaging)
   */
  public void messageAllClients(PushMessage myPushMessage) {
    logger.info("Dispatching broadcast message to all registered clients");

    String messageString = gson.toJson(myPushMessage);
    JsonObject jData = new JsonParser().parse(messageString).getAsJsonObject();

    for (String clientToken : registeredClients) {
      HowAlarmingGcmServer.send(clientToken, jData);
    }
  }



  // MARK: main()

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
