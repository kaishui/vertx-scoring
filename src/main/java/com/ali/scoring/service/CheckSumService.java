package com.ali.scoring.service;


import com.ali.scoring.config.Constants;
import com.ali.scoring.config.Utils;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;


public class CheckSumService implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(CheckSumService.class);
    private AtomicInteger FINISH_COUNT = new AtomicInteger(0);


    // save chuckSum for the total wrong trace
    private static Map<String, String> TRACE_CHUCKSUM_MAP = new ConcurrentHashMap<>();
    private final Vertx vertx;

    public CheckSumService(Vertx vertx) {
        this.vertx = vertx;
    }

    public static void start(Vertx vertx) {
        Thread t = new Thread(new CheckSumService(vertx), "CheckSumServiceThread");
        t.start();
    }

    @Override
    public void run() {
        //get md5 for each batch
        vertx.eventBus().consumer("getMD5", consumer -> {
            getBadTraceMD5((JsonObject) consumer.body());
        });


        vertx.eventBus().consumer("sendCheckSum", consumer -> {
            sendCheckSum();
        });
    }

    public void getBadTraceMD5(JsonObject traceIdBatchJson) {
        JsonObject traceIdBatchMap = traceIdBatchJson.getJsonObject("badTraceRecords");
        traceIdBatchMap.stream().iterator().forEachRemaining(entry ->{
            String traceId = entry.getKey();
            List<String> spanSet = ((JsonArray) entry.getValue()).getList();
            // order span with startTime
            String spans = spanSet.stream().sorted(
                    Comparator.comparing(CheckSumService::getStartTime)).collect(Collectors.joining("\n"));
            spans = spans + "\n";
            // output all span to check
            LOGGER.info("TRACE_CHUCKSUM_MAP traceId:" + traceId);
            TRACE_CHUCKSUM_MAP.put(traceId, Utils.MD5(spans));
        });
        if (traceIdBatchJson.getBoolean("isLastUpdate")) {
            if (FINISH_COUNT.incrementAndGet() >= Constants.PROCESS_COUNT) {
                vertx.eventBus().send("sendCheckSum", new JsonObject());
            }
        }
    }

    public static void mergeTraceDatas(Map<String, List<String>> result, Map<String, List<String>> toMerge) {
        LOGGER.debug("mergeTraceDatas: " + Json.encode(toMerge));
        for (Map.Entry<String, List<String>> entry : toMerge.entrySet()) {
            String traceId = entry.getKey();
            List<String> spanSet = result.computeIfAbsent(traceId, k -> new ArrayList<>());
            spanSet.addAll(entry.getValue());
        }
    }

    /**
     * call client process, to get all spans of wrong traces.
     *
     * @param traceIdList
     * @param port
     * @param batchPos
     * @param promise
     * @return
     */
    private void getWrongTrace(List<String> traceIdList, String port, int batchPos, Promise<Map<String, List<String>>> promise) {
        JsonObject jsonBody = new JsonObject();
        jsonBody.put("badTraceIdList", traceIdList).put("batchPos", batchPos);

        vertx.eventBus().request("getWrongTrace" + port, jsonBody, handler -> {
            Map<String, List<String>> result = Json.decodeValue((String) handler.result().body(), Map.class);
            promise.complete(result);
        });

    }


    private boolean sendCheckSum() {
        String result = Json.encode(TRACE_CHUCKSUM_MAP);
        StringBuffer params = new StringBuffer().append("result=").append(result);
        LOGGER.warn("params: " + params);
        //call http api to get trace data
        HttpRequest.BodyPublisher body = HttpRequest.BodyPublishers.ofString(params.toString());
        String url = String.format("http://localhost:%s/api/finished", Utils.sendToApiPort());

        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(body).build();
        //output stream with lines
        HttpResponse.BodyHandler<String> bodyHandler = HttpResponse.BodyHandlers.ofString();
        HttpClient client = HttpClient.newHttpClient();
        try {
            HttpResponse<String> bodyResult = client.send(request, bodyHandler);
            LOGGER.debug("send to :" + url + ", result:" + bodyResult.statusCode() + " " + bodyResult.body());
            return bodyResult.statusCode() == 200;
        } catch (Exception e) {
            LOGGER.error(e);
        }
        return false;
    }

    public static long getStartTime(String span) {
        if (span != null) {
            int index = span.indexOf("|");
            //1589285985534167 length
            String timestamp = span.substring(index + 1, index + 17);
            return Utils.toLong(timestamp, -1);
        }
        return -1;
    }
}
