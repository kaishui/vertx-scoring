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
            TraceDataService.start(vertx);
            HttpServerResponse response = ctx.response();
            // Write to the response and end it
            response.end("suc");
        });

        router.get("/test").handler(ctx -> {
            DATA_SOURCE_PORT = ctx.request().getParam("port");
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
