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


