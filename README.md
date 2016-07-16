# HowAlarming GCM Server

This small Java application provides a bridge between
[HowAlarming](https://github.com/jethrocarr/howalarming) and Google Compute
Messaging (GCM) used by the companion Android and iOS apps.

Unlike more lightweight implementations with the HTTP service, this service
offers bi-directional communication between applications which enables in-app
actions to be relayed through to the alarm system.

XMPP handling code based heavily on the FriendlyPing example application
published by Google.


# Application Registration

Application registration is automatic - when the phone apps are launched they
send a registration message to the server and are added to the list of known
devices to send push messages to.

TODO: We need to set this up to save state, currently it only remains in the
server for the duration of it's run time.


# Requirements

This application should build and execute with Java 7 or Java 8.


# Build & Execution

You can build the application as a standalone JAR file using `gradle` by
running:

    gradle fatJar

This creates a standalone JAR file in `./build/libs/HowAlarmingServer-all-VERSON.jar`
which includes all the dependencies and is ready-to-run as a foreground server
with:

    export GCM_SERVER_ID=123
    export GCM_API_KEY=abc
    export BEANSTALK_HOST=127.0.0.1
    export BEANSTALK_PORT=11300
    export BEANSTALK_TUBES_EVENTS=alert_gcm
    export BEANSTALK_TUBES_COMMANDS=commands
    java -jar ./build/libs/HowAlarmingServer-all-VERSON.jar

All configuration is specified via environmentals, as per the above example.


# Easy operation

A wrapper launcher ships as part of the [HowAlarming](https://github.com/jethrocarr/howalarming)
project, which allows the Java daemon to be run easily via the [Puppet module provided](https://github.com/jethrocarr/puppet-howalarming).


# License

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at
    
    http://www.apache.org/licenses/LICENSE-2.0
    
    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.


