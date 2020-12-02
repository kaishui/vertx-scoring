package com.ali.scoring.service;

import com.hazelcast.core.HazelcastInstance;
import io.vertx.core.Vertx;

public class VertxInstanceService {

    private static Vertx vertx;
    private static HazelcastInstance hazelcastInstance;


    public static void setVertx(Vertx vertx) {
        VertxInstanceService.vertx = vertx;
        VertxInstanceService.hazelcastInstance = HazelcastService.getInstance(vertx);
    }

    public static Vertx getVertx() {
        return vertx;
    }

    public static HazelcastInstance getHazelcastInstance() {
        return hazelcastInstance;
    }
}
