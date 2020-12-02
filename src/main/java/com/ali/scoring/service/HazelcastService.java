package com.ali.scoring.service;

import com.hazelcast.core.HazelcastInstance;
import io.vertx.core.Vertx;
import io.vertx.core.impl.VertxImpl;
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;

public class HazelcastService {

    public static HazelcastInstance getInstance(Vertx vertx){
        HazelcastClusterManager hazelcastClusterManager = (HazelcastClusterManager) ((VertxImpl) vertx).getClusterManager();
        HazelcastInstance hazelcastInstance = hazelcastClusterManager.getHazelcastInstance();
        return hazelcastInstance;
    }

}
