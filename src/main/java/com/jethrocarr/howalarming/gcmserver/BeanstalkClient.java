package com.jethrocarr.howalarming.gcmserver;

import com.dinstone.beanstalkc.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Observable;
import java.util.logging.Level;
import java.util.logging.Logger;

/*
  BeanstalkClient defines a class for exchanging messages to/from Beanstalk
 */
public class BeanstalkClient extends HowAlarmingConfig {

    private static final Logger logger = Logger.getLogger("BeanstalkClient");

    private Configuration beanstalkConfig;
    private BeanstalkClientFactory beanstalkFactory;

    public BeanstalkClient() {
        // Connect to beanstalk queue
        logger.info("Listening to beanstalk queue on "+ BEANSTALK_HOST +":"+ BEANSTALK_PORT);

        beanstalkConfig = new Configuration();
        beanstalkConfig.setServiceHost(BEANSTALK_HOST);
        beanstalkConfig.setServicePort(Integer.parseInt(BEANSTALK_PORT));

        beanstalkFactory = new BeanstalkClientFactory(beanstalkConfig);

        // Launch the tube listener in a dedicated thread.
        BeanstalkClientListener beanstalkIncoming = new BeanstalkClientListener();


        Thread beanstalkClientThread = new Thread(beanstalkIncoming);
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
    public class BeanstalkClientListener implements Runnable {

        public void run() {

            // TODO: hardcoded
            logger.info("Running Beanstalk listener against alert_gcm");
            JobConsumer consumer = beanstalkFactory.createJobConsumer("alert_gcm");

            while (true) {

                // Poll for new messages to process
                try {
                    Job job = consumer.reserveJob(60);

                    if (job != null) {
                        // Obtain the message and push to all clients

                        // TODO: This could almost certainly be re-worked into less mess
                        String message = new String(job.getData());
                        JsonObject messageJson;

                        try {
                            messageJson = new JsonParser().parse(message).getAsJsonObject();

                            // Is this a message type we actually want to send?
                            // TODO: This should be loaded from config.
                            switch (messageJson.get("type").getAsString()) {
                                case "alarm":
                                case "recovery":
                                case "fault":
                                case "armed":
                                case "disarmed":
                                    // Valid message type for alerting, send.
                                    PushMessage myPushMessage = new PushMessage();
                                    myPushMessage.fromBeanstalk(messageJson);

                                    // We need to get our PushMesaage through to the GCM server in another
                                    // thread, so we use an observer to trigger an update on an event.
                                    messageAllClients.send(myPushMessage);

                                    break;

                                default:
                                    logger.log(Level.INFO, "Not transmitting event of type: " + messageJson.get("type"));
                                    break;

                            }

                        } catch (java.lang.IllegalStateException e) {
                            logger.log(Level.WARNING, "Received invalid JSON message, deleting and skipping " + message, e);
                        }

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