package com.jethrocarr.howalarming.gcmserver;

import java.util.List;
import java.util.Observable;
import java.util.Observer;

/**
 * Global configuration parameters used throughout the application
 */
public class HowAlarmingConfig {

    // Creds
    public static final String SERVER_API_KEY      = System.getenv("GCM_API_KEY");
    public static final String SENDER_ID           = System.getenv("GCM_SENDER_ID");

    // Beanstalk Queue
    public static String BEANSTALK_HOST            = System.getenv("BEANSTALK_HOST");
    public static String BEANSTALK_PORT            = System.getenv("BEANSTALK_PORT");
    public static String BEANSTALK_TUBES_EVENTS    = System.getenv("BEANSTALK_TUBES_EVENTS");
    public static String BEANSTALK_TUBES_COMMANDS  = System.getenv("BEANSTALK_TUBES_COMMANDS");

    // Observer used to send messages from Beanstalk reads through to GCM pushes
    public static PushMessageDispatch messageAllClients = new PushMessageDispatch();

    /**
     * Validate all the required configuration in the constructor, and set defaults as required.
     */
    public HowAlarmingConfig() {
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
    }

}


