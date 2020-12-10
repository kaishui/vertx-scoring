package com.ali.scoring.service;

import com.ali.scoring.config.Constants;
import com.ali.scoring.config.Utils;
import com.ali.scoring.controller.CommonController;
import io.netty.util.internal.StringUtil;
import io.vertx.core.Promise;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

public class TraceDataService implements Runnable {
    private static Logger logger = LoggerFactory.getLogger(TraceDataService.class);

    private final Vertx vertx;

    public TraceDataService(Vertx vertx) {
        this.vertx = vertx;
    }

    // an list of trace map,like ring buffe.  key is traceId, value is spans
    private static List<Map<String, List<String>>> BATCH_TRACE_LIST = new ArrayList<>();

//    //6w TODO 1
//    Multimap<String, String> traceRecords = ArrayListMultimap.create();
//    //batch bad traceIds
//    Map<Integer, Set<String>> keyPairs = new ConcurrentHashMap<>();

    //TODO SOLUTION 2
//    // list<timestamp, lines>
//    List<Map<Long, List<String>>> TIME_TRACE_LIST = new ArrayList<>();
//
//    // list<timestamp, traceIds>
//    List<Map<Long, List<String>>> TIME_TRACEID_LIST = new ArrayList<>);
//    //动态调整 trace timestamp index
//    List<Long> badTraceTimestamps = new ArrayList<>();



    // make 15 bucket to cache traceData
    private static int BATCH_COUNT = 20;

    private static AtomicInteger badTraceSize = new AtomicInteger(0);

    // 15 * 20k = 300k
    public static void init() {
        for (int i = 0; i < BATCH_COUNT; i++) {
            BATCH_TRACE_LIST.add(new ConcurrentHashMap<>(Constants.BATCH_SIZE));
        }
    }

    public static void start(Vertx vertx) {
        Thread t = new Thread(new TraceDataService(vertx), "ProcessDataThread");
        t.start();
    }

    @Override
    public void run() {
        //load data
        String port = System.getProperty("server.port", "8080");
        if ("8000".equals(port) || "8001".equals(port)) {
            try {
                Set<String> badTraceIds = new HashSet<>(100);

                //call http api to get trace data
                HttpRequest request = HttpRequest.newBuilder().uri(URI.create(getPath())).GET().build();
                //outputstream with lines
                HttpResponse.BodyHandler<Stream<String>> bodyHandler = HttpResponse.BodyHandlers.ofLines();
                HttpClient client = HttpClient.newHttpClient();
                HttpResponse<Stream<String>> response = client.send(request, bodyHandler);

                final AtomicInteger pos = new AtomicInteger(0);
                AtomicLong count = new AtomicLong(0L);
                Stream<String> lines = response.body();
                // error log
                //error=1 | http.status_code=xxx - 3,  needle string len is 4
                lines.forEach(line -> {
                    count.incrementAndGet();
                    int len = line.length();
                    //error=1 | http.status_code=xxx - 3,  needle string len is 4
                    String endFlag = line.substring(len - 7, len - 3);
                    String traceId = line.substring(0, line.indexOf("|"));

                    //set cache data for saving the before data
                    Map<String, List<String>> traceMap = BATCH_TRACE_LIST.get(pos.get());
                    List<String> spanList = traceMap.computeIfAbsent(traceId, k -> new ArrayList<>());

                    spanList.add(line);

                    //save error or http status != 200
                    if (endFlag.equals("erro") || endFlag.equals("ode=")) {
                        badTraceIds.add(traceId);
                    }

                    // 20K 数据一个Batch
                    if (count.get() % Constants.BATCH_SIZE == 0) {
                        // loop cycle
                        if (pos.incrementAndGet() >= BATCH_COUNT) {
                            pos.set(0);
                        }
                        // batchPos begin from 0, so need to minus 1
                        int batchPos = (int) (count.get() / Constants.BATCH_SIZE) - 1;
                        getAnotherPartBadTraceData(badTraceIds, batchPos, false);
                        logger.info("suc to updateBadTraceId, badTraceIds size:" + badTraceSize.addAndGet(badTraceIds.size()) + " batchPos:" + batchPos);
                        badTraceIds.clear();
                    }
                });

                //剩下的batch update
                getAnotherPartBadTraceData(badTraceIds, (int) (count.get() / Constants.BATCH_SIZE) - 1, true);
                logger.info("suc to updateBadTraceId, badTraceIds size:" + badTraceSize.addAndGet(badTraceIds.size()));
            } catch (Exception e) {

            }
        }
    }

    private void getAnotherPartBadTraceData(Set<String> badTraceIds, int batchPos, boolean isLastUpdate) {
        Set<String> badIds = new HashSet<>(badTraceIds);
        io.vertx.core.Future<Map<String, List<String>>> future = io.vertx.core.Future.future(promise -> {
            updateWrongTraceId(badIds, batchPos, false, promise);
        });
        future.onSuccess(result ->{
            JsonObject params = new JsonObject();
            params.put("isLastUpdate", isLastUpdate).put("badTraceRecords", result);
            //GET BAD TRACE MD5
            vertx.eventBus().send("getMD5", params);
        });
    }


    public static Map<String, List<String>> getWrongTracing(List<String> traceIdList, int batchPos) {
        Map<String, List<String>> wrongTraceMap = new HashMap<>();
        int pos = batchPos % BATCH_COUNT;
        int previous = pos - 1;
        if (previous == -1) {
            previous = BATCH_COUNT - 1;
        }
        int next = pos + 1;
        if (next == BATCH_COUNT) {
            next = 0;
        }
        getWrongTraceWithBatch(previous, pos, traceIdList, wrongTraceMap);
        getWrongTraceWithBatch(pos, pos, traceIdList, wrongTraceMap);
        getWrongTraceWithBatch(next, pos, traceIdList, wrongTraceMap);
        // to clear spans, don't block client pess thread. TODO to use lock/notify
        CompletableFuture<Integer> future = CompletableFuture.completedFuture(previous);
        future.whenComplete((index, ex) ->{
            BATCH_TRACE_LIST.get(index).clear();
        });
        logger.debug("wrongTraceMap result:" + wrongTraceMap.size());
        return wrongTraceMap;
    }

    private static void getWrongTraceWithBatch(int batchPos, int pos, List<String> traceIdList, Map<String, List<String>> wrongTraceMap) {
        // donot lock traceMap,  traceMap may be clear anytime.
        Map<String, List<String>> traceMap = BATCH_TRACE_LIST.get(batchPos);
        for (String traceId : traceIdList) {
            List<String> spanList = traceMap.get(traceId);
            if (spanList != null) {
                // one trace may cross to batch (e.g batch size 20000, span1 in line 19999, span2 in line 20001)
                List<String> existSpanList = wrongTraceMap.get(traceId);
                if (existSpanList != null) {
                    existSpanList.addAll(spanList);
                } else {
                    wrongTraceMap.put(traceId, spanList);
                }
            }
        }
    }

    /**
     * call backend controller to update wrong tradeId list.
     *  @param badTraceIdList
     * @param batchPos
     * @param promise
     */
    private void updateWrongTraceId(Set<String> badTraceIdList, int batchPos, boolean isLastUpdate, Promise<Map<String, List<String>>> promise) {
        if (badTraceIdList.size() > 0) {

            logger.debug("updateWrongTraceId: " + badTraceIdList.size());
            JsonObject jsonBody = new JsonObject();
            jsonBody.put("badTraceIdList", new ArrayList<>(badTraceIdList));
            jsonBody.put("batchPos", batchPos);

            //batch 1
            Map<String, List<String>> result1 = getWrongTracing(new ArrayList<>(badTraceIdList), batchPos);

            io.vertx.core.Future<Map<String, List<String>>> future = io.vertx.core.Future.future(getAnotherDataPromise -> {
                //batch 2 trace data
                vertx.eventBus().request(Utils.getWrongPath(), jsonBody, handler -> {
                    if (handler.succeeded()) {
                        String result = (String) handler.result().body();
                        Map<String, List<String>> resultMap = Json.decodeValue(result, Map.class);
                        getAnotherDataPromise.complete(resultMap);
                    } else {
                        logger.warn("get another trace data failed");
                        getAnotherDataPromise.complete(new ConcurrentHashMap<>());
                    }
                });
            });
            future.onSuccess(value ->{
                CheckSumService.mergeTraceDatas(result1, value);
                promise.complete(result1);
            });
        }
    }

    private String getPath() {
        String port = System.getProperty("server.port", "8080");
        if ("8000".equals(port)) {
            return "http://localhost:" + CommonController.getDataSourcePort() + "/trace1.data";
        } else if ("8001".equals(port)) {
            return "http://localhost:" + CommonController.getDataSourcePort() + "/trace2.data";
        } else {
            return null;
        }
    }

}
