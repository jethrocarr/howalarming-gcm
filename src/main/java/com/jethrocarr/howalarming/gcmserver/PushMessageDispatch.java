package com.jethrocarr.howalarming.gcmserver;

import java.util.Observable;


/**
 * We need an observable object to allow us to dispatch messages between the beanstalk thread and the main
 * application upon receipt of messages.
 */

public class PushMessageDispatch extends Observable {

    public void send(PushMessage myPushMessage) {
        setChanged();
        notifyObservers(myPushMessage);
    }

}