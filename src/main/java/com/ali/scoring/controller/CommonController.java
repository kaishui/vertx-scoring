package com.ali.scoring.controller;

import com.ali.scoring.service.TraceDataService;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;

import java.util.List;
import java.util.Map;


public class CommonController {

    private static Logger logger = LoggerFactory.getLogger(CommonController.class);
    private static String DATA_SOURCE_PORT = "0";

    public static Router router(Vertx vertx) {
        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());


        String port = System.getProperty("server.port", "8000");

        vertx.eventBus().consumer("getWrongTrace" + port, handler -> {
            JsonObject body = (JsonObject) handler.body();
            logger.debug("getWrongTrace" + port + " " + body.encode());
            List<String> traceIdList = body.getJsonArray("badTraceIdList").getList();
            int batchPos = body.getInteger("batchPos");
            Map<String, List<String>> result = TraceDataService.getWrongTracing(traceIdList, batchPos);
            handler.reply(Json.encode(result));
        });


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

        return router;
    }


    public static String getDataSourcePort() {
        return DATA_SOURCE_PORT;
    }


}
