package com.ali.scoring.controller;

import com.ali.scoring.service.TraceDataService;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.multimap.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.impl.VertxImpl;
import io.vertx.ext.web.Router;
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;

import java.util.Date;
import java.util.List;


public class CommonController {
    private static String DATA_SOURCE_PORT = "0";

    public static Router router(Vertx vertx) {
        Router router = Router.router(vertx);


        router.get("/ready").handler(ctx -> {
            List<String> nodes = ((VertxImpl) vertx).getClusterManager().getNodes();
            HazelcastClusterManager hazelcastClusterManager = (HazelcastClusterManager) ((VertxImpl) vertx).getClusterManager();
            HazelcastInstance hazelcastInstance = hazelcastClusterManager.getHazelcastInstance();
            MultiMap<String, String> map = hazelcastInstance.getMultiMap("default");
            map.put("port", new Date().toString());

            System.out.println(map.toString());

            System.out.println(nodes.toString());
            HttpServerResponse response = ctx.response();
            response.putHeader("content-type", "text/plain");
            // Write to the response and end it
            response.end("suc");
        });

        router.get("/start").handler(ctx -> {
            HttpServerResponse response = ctx.response();
            response.putHeader("content-type", "text/plain");
            // Write to the response and end it
            response.end("suc");
        });

        router.get("/setParameter").handler(ctx -> {
            HttpServerRequest request = ctx.request();
            DATA_SOURCE_PORT = request.getParam("port");
            TraceDataService.start();
            HttpServerResponse response = ctx.response();
            // Write to the response and end it
            response.end("suc");
        });

        return router;
    }


    public static String getDataSourcePort() {
        return DATA_SOURCE_PORT;
    }


}
