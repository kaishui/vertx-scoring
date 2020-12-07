package com.ali.scoring.service;


import com.ali.scoring.config.Constants;
import com.ali.scoring.config.Utils;
import com.ali.scoring.controller.BackendController;
import com.ali.scoring.controller.CommonController;
import com.ali.scoring.controller.TraceIdBatch;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;


public class CheckSumService implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(BackendController.class);


    // save chuckSum for the total wrong trace
    private static Map<String, String> TRACE_CHUCKSUM_MAP = new ConcurrentHashMap<>();

    public static void start() {
        Thread t = new Thread(new CheckSumService(), "CheckSumServiceThread");
        t.start();
    }

    @Override
    public void run() {
        TraceIdBatch traceIdBatch = null;
        String[] ports = new String[]{Constants.CLIENT_PROCESS_PORT1, Constants.CLIENT_PROCESS_PORT2};
        while (true) {
            try {
                traceIdBatch = BackendController.getFinishedBatch();
                if (traceIdBatch == null) {
                    // send checksum when client process has all finished.
                    if (BackendController.isFinished()) {
                        if(sendCheckSum()){
                            break;
                        }
                    }
                    continue;
                }
                Map<String, Set<String>> map = new HashMap<>();
                // if (traceIdBatch.getTraceIdList().size() > 0) {
                int batchPos = traceIdBatch.getBatchPos();
                // to get all spans from remote
                for (String port : ports) {
                    getWrongTrace(traceIdBatch.getTraceIdList(), port, batchPos, processMap -> {
                        if (processMap != null) {
                            for (Map.Entry<String, List<String>> entry : processMap.entrySet()) {
                                String traceId = entry.getKey();
                                Set<String> spanSet = map.computeIfAbsent(traceId, k -> new HashSet<>());
                                spanSet.addAll(entry.getValue());
                            }
                        }
                    });
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
                    TRACE_CHUCKSUM_MAP.put(traceId, Utils.MD5(spans));
                }
            } catch (Exception e) {
                // record batchPos when an exception  occurs.
                int batchPos = 0;
                if (traceIdBatch != null) {
                    batchPos = traceIdBatch.getBatchPos();
                }
                LOGGER.warn(String.format("fail to getWrongTrace, batchPos:%d", batchPos), e);
            } finally {
                if (traceIdBatch == null) {
                    try {
                        Thread.sleep(100);
                    } catch (Throwable e) {
                        // quiet
                    }
                }
            }
        }
    }

    /**
     * call client process, to get all spans of wrong traces.
     *
     * @param traceIdList
     * @param port
     * @param batchPos
     * @return
     */
    private Map<String, List<String>> getWrongTrace(List<String> traceIdList, String port, int batchPos, Consumer<Map<String, List<String>>> consumer) {
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
        CompletableFuture<HttpResponse<String>> future = client.sendAsync(request, bodyHandler);
        future.whenComplete((res, ex) -> {
            LOGGER.debug(res.body());

            Map<String, List<String>> map = Json.decodeValue(res.body(), Map.class);
            consumer.accept(map);
        });


        return null;
    }


    private boolean sendCheckSum() {
        String result = Json.encode(TRACE_CHUCKSUM_MAP);
        JsonObject jsonBody = new JsonObject();
        jsonBody.put("result", result);
        //call http api to get trace data
        HttpRequest.BodyPublisher body = HttpRequest.BodyPublishers.ofString(jsonBody.encode());
        String url = String.format("http://localhost:%s/api/finished", Utils.sendToApiPort());

        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url))
                .POST(body).build();
        //output stream with lines
        HttpResponse.BodyHandler<String> bodyHandler = HttpResponse.BodyHandlers.ofString();
        HttpClient client = HttpClient.newHttpClient();
        try {
            HttpResponse<String> bodyResult = client.send(request, bodyHandler);
            LOGGER.debug("send to :" + url + ", result: {}" + bodyResult.body());
            return bodyResult.statusCode() == 200;
        }  catch (Exception e) {
            LOGGER.error(e);
        }
        return false;
    }

    public static long getStartTime(String span) {
        if (span != null) {
            String[] cols = span.split("\\|");
            if (cols.length > 8) {
                return Utils.toLong(cols[1], -1);
            }
        }
        return -1;
    }


}
