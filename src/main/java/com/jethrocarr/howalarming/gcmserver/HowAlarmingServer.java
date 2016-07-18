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

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;
import java.util.logging.Logger;



/**
 * HowAlarmingServer provides the logic for registering devices and providing communication between the server
 * and the client devices/apps.
 */
public class HowAlarmingServer extends HowAlarmingConfig {


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

            String registration_token = new String();

            if (jData.has("registration_token")) {
                registration_token = jData.get("registration_token").getAsString();
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

                    case "ping":
                        /**
                         * A ping is sent by the mobile app every time it starts. This ensures that the app gets registered if the
                         * GCM server has been reset, but also allows us to send back the current alarm status for the UI to know
                         * whether it is armed/disarmed.
                         */
                        logger.info("Received ping from device, sending back status.");

                        if (registration_token != null) {
                            PushMessage myPushMessage = new PushMessage();
                            myPushMessage.alarmStatus(stateArmed);

                            JsonObject myPushJMessageJson = new JsonParser().parse(gson.toJson(myPushMessage)).getAsJsonObject();

                            try {
                                HowAlarmingGcmServer.send(registration_token, myPushJMessageJson);
                            } catch (Exception e) {
                                logger.log(Level.SEVERE, "An unexpected error occurred attempting to message device: " + registration_token, e);
                            }
                        }

                        break;

                    default:
                        logger.warning("Command "+ command +" is not a support simple command type, unable to action message");
                }


            } else {
                logger.info("Unexpected message received from GCM, ignoring.");
            }
        }
    }



    // MARK: Properties


    private static final Logger logger = Logger.getLogger("HowAlarmingServer");
    public static final String SERVICE_NAME = "HowAlarming GCM Server";



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

        registeredClients = new ArrayList<String>();
        gson = new GsonBuilder().create();

        beanstalkClient = new BeanstalkClient();
        HowAlarmingGcmServer = new HowAlarmingGcmServer(apiKey, senderId, SERVICE_NAME);

        messageAllClients.addObserver(new messageAllClients());

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
     * Deliver a message to all registered clients (basically broadcast, we don't need to care about 1-1 messaging)
     */
    private class messageAllClients implements Observer {
        public void update(Observable obj, Object arg) {

            PushMessage myPushMessage = (PushMessage) arg;

            logger.info("Dispatching broadcast message to all registered clients");

            String messageString = gson.toJson(myPushMessage);
            JsonObject jData = new JsonParser().parse(messageString).getAsJsonObject();

            for (String clientToken : registeredClients) {
                try {
                    HowAlarmingGcmServer.send(clientToken, jData);
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "An unexpected error occurred attempting to message device: " + clientToken, e);
                }
            }
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
