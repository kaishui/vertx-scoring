package com.ali.scoring.service;


import com.ali.scoring.config.Constants;
import com.ali.scoring.config.Utils;
import com.hazelcast.cp.IAtomicLong;
import com.hazelcast.map.IMap;
import io.vertx.core.Vertx;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;


public class CheckSumService implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(CheckSumService.class);


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
            IAtomicLong atomicLongConsumer = VertxInstanceService.getHazelcastInstance().getCPSubsystem().getAtomicLong("consumer");
            atomicLongConsumer.getAndIncrement();
            getBadTraceMD5((JsonObject) consumer.body());
            consumer.reply(new JsonObject().put("result", "suc"));
        });

        vertx.eventBus().consumer("sendCheckSum", consumer -> {
            IAtomicLong atomicLongConsumer = VertxInstanceService.getHazelcastInstance().getCPSubsystem().getAtomicLong("consumer");
            IAtomicLong atomicLongSender = VertxInstanceService.getHazelcastInstance().getCPSubsystem().getAtomicLong("sender");
            if (atomicLongConsumer.get() == atomicLongSender.get()) {
                sendCheckSum();
            }
        });
    }

    public static void getBadTraceMD5(JsonObject traceIdBatch) {

        Map<String, Set<String>> map = new HashMap<>();
        String[] ports = new String[]{Constants.CLIENT_PROCESS_PORT1, Constants.CLIENT_PROCESS_PORT2};

        // if (traceIdBatch.getTraceIdList().size() > 0) {
        int batchPos = traceIdBatch.getInteger("batchPos");

        // to get all spans from remote
        for (String port : ports) {
            Map<String, List<String>> processMap = getWrongTrace(traceIdBatch.getJsonArray("badTraceIdList").getList(), port, batchPos);
            if (processMap != null) {
                for (Map.Entry<String, List<String>> entry : processMap.entrySet()) {
                    String traceId = entry.getKey();
                    Set<String> spanSet = map.computeIfAbsent(traceId, k -> new HashSet<>());
                    spanSet.addAll(entry.getValue());
                }
            }
        }

        for (Map.Entry<String, Set<String>> entry : map.entrySet()) {
            String traceId = entry.getKey();
            Set<String> spanSet = entry.getValue();
            // order span with startTime
            String spans = spanSet.stream().sorted(
                    Comparator.comparing(CheckSumService::getStartTime)).collect(Collectors.joining("\n"));
            spans = spans + "\n";
            // output all span to check
            // LOGGER.info("traceId:" + traceId + ",value:\n" + spans);
            IMap<Object, Object> checkSumMap = VertxInstanceService.getHazelcastInstance().getMap("checkSumMap");
            checkSumMap.put(traceId, Utils.MD5(spans));
        }

        VertxInstanceService.getVertx().eventBus().send("sendCheckSum", new JsonObject());
    }

    /**
     * call client process, to get all spans of wrong traces.
     *
     * @param traceIdList
     * @param port
     * @param batchPos
     * @return
     */
    private static Map<String, List<String>> getWrongTrace(List<String> traceIdList, String port, int batchPos) {
        JsonObject jsonBody = new JsonObject();
        jsonBody.put("badTraceIdList", traceIdList).put("batchPos", batchPos);
        //call http api to get trace data
        HttpRequest.BodyPublisher body = HttpRequest.BodyPublishers.ofString(jsonBody.encode());

        String url = String.format("http://localhost:%s/getWrongTrace", port);

        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url))
                .POST(body).build();
        //outputstream with lines
        HttpResponse.BodyHandler<String> bodyHandler = HttpResponse.BodyHandlers.ofString();
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> res = null;
        try {
            res = client.send(request, bodyHandler);
        } catch (Exception e) {
            LOGGER.error("getWrongTrace exception", e);
        }
        if (res != null && res.statusCode() == 200) {
            Map<String, List<String>> map = Json.decodeValue(res.body(), Map.class);
            return map;
        }
        return null;
    }


    private boolean sendCheckSum() {
        IMap<Object, Object> checkSumMap = VertxInstanceService.getHazelcastInstance().getMap("checkSumMap");

        String result = Json.encode(checkSumMap);
        StringBuffer params = new StringBuffer().append("result=").append(result);
        LOGGER.debug("params: " + params);
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
