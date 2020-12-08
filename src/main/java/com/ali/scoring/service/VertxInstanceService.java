package com.ali.scoring.service;

import com.hazelcast.core.HazelcastInstance;
import io.vertx.core.Vertx;
import io.vertx.core.impl.VertxImpl;
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;

public class VertxInstanceService {

    private static Vertx vertx;
    private static HazelcastInstance hazelcastInstance;


    public static void setVertx(Vertx vertx) {
        VertxInstanceService.vertx = vertx;
        VertxInstanceService.hazelcastInstance = getInstance(vertx);
    }

    public static Vertx getVertx() {
        return vertx;
    }

    public static HazelcastInstance getHazelcastInstance() {
        return hazelcastInstance;
    }

    public static HazelcastInstance getInstance(Vertx vertx){
        HazelcastClusterManager hazelcastClusterManager = (HazelcastClusterManager) ((VertxImpl) vertx).getClusterManager();
        HazelcastInstance hazelcastInstance = hazelcastClusterManager.getHazelcastInstance();
        return hazelcastInstance;
    }
}
