package com.ali.scoring.controller;

import com.ali.scoring.service.TraceDataService;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;

import java.util.List;

public class ClientController {


    public static void router(Router router) {
        router.post("/getWrongTrace").handler(ctx -> {
            JsonObject body = ctx.getBodyAsJson();
            List<String> traceIdList = body.getJsonArray("badTraceIdList").getList();
            int batchPos = body.getInteger("batchPos");
            String json = TraceDataService.getWrongTracing(traceIdList, batchPos);

            ctx.response().send(json);
        });
    }
}
