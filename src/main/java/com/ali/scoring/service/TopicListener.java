package com.ali.scoring.service;

import com.hazelcast.cp.IAtomicLong;
import com.hazelcast.topic.Message;
import com.hazelcast.topic.MessageListener;

public class TopicListener implements MessageListener<String> {
    @Override
    public void onMessage(Message<String> message) {

        if (getLong() >= 1L) {

        }
    }

    public long getLong() {
        IAtomicLong counter = VertxInstanceService.getHazelcastInstance().getCPSubsystem().getAtomicLong( "counter" );
        return counter.incrementAndGet();
    }
}
