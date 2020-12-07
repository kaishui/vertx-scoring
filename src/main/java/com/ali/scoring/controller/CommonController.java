package com.ali.scoring.controller;

import com.ali.scoring.service.TraceDataService;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.multimap.MultiMap;
import com.hazelcast.ringbuffer.Ringbuffer;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.impl.VertxImpl;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;

import java.util.Date;
import java.util.List;


public class CommonController {
    private static String DATA_SOURCE_PORT = "0";

    public static Router router(Vertx vertx) {
        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());


        router.get("/ready").handler(ctx -> {
//            List<String> nodes = ((VertxImpl) vertx).getClusterManager().getNodes();
//            HazelcastClusterManager hazelcastClusterManager = (HazelcastClusterManager) ((VertxImpl) vertx).getClusterManager();
//            HazelcastInstance hazelcastInstance = hazelcastClusterManager.getHazelcastInstance();
//            MultiMap<String, String> map = hazelcastInstance.getMultiMap("default");
//            Ringbuffer<Object> rb = hazelcastInstance.getRingbuffer("rb");
//            map.put("port", new Date().toString());
//
//            for (int i = 0; i < 20; i++) {
//                rb.add("" + i);
//            }
//            System.out.println(rb.capacity());
//            System.out.println(rb.size());
//
//            System.out.println(map.toString());
//
//            System.out.println(nodes.toString());
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
            DATA_SOURCE_PORT = ctx.request().getParam("port");
            TraceDataService.start();
            HttpServerResponse response = ctx.response();
            // Write to the response and end it
            response.end("suc");
        });

        router.get("/test").handler(ctx -> {
            DATA_SOURCE_PORT = ctx.request().getParam("port");
            vertx.eventBus().send("send-to-test", new JsonObject().put("port", DATA_SOURCE_PORT));
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
