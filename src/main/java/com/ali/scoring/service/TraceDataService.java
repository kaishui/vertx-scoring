package com.ali.scoring.service;

import com.ali.scoring.config.Constants;
import com.ali.scoring.controller.CommonController;
import com.hazelcast.cp.IAtomicLong;
import io.vertx.core.Handler;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

public class TraceDataService implements Runnable {
    private static Logger logger = LoggerFactory.getLogger(TraceDataService.class);

    private final Vertx vertx;

    public TraceDataService(Vertx vertx) {
        this.vertx = vertx;
    }

    // an list of trace map,like ring buffe.  key is traceId, value is spans ,  r
    private static List<Map<String, List<String>>> BATCH_TRACE_LIST = new ArrayList<>();
    // make 15 bucket to cache traceData
    private static int BATCH_COUNT = 100;

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
            long startTime = System.nanoTime();
            try {
                Set<String> badTraceIds = new HashSet<>(1000);

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
                    count.addAndGet(1l);
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
                        pos.addAndGet(1);
                        // loop cycle
                        if (pos.get() >= BATCH_COUNT) {
                            pos.set(0);
                        }
                        // batchPos begin from 0, so need to minus 1
                        int batchPos = (int) (count.get() / Constants.BATCH_SIZE) - 1;
                        updateWrongTraceId(badTraceIds, batchPos);

                        logger.info("suc to updateBadTraceId, badTraceIds size:" + badTraceSize.addAndGet(badTraceIds.size()) + " batchPos:" + batchPos);
                        badTraceIds.clear();
                    }
                });

                //剩下的batch update
                updateWrongTraceId(badTraceIds, (int) (count.get() / Constants.BATCH_SIZE) - 1);
                logger.info("suc to updateBadTraceId, badTraceIds size:" + badTraceSize.addAndGet(badTraceIds.size()));
            } catch (Exception e) {

            } finally {
                long endTime = System.nanoTime();
                logger.debug("time:" + (endTime - startTime));
            }
        }
    }


    public static String getWrongTracing(List<String> traceIdList, int batchPos) {
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
        // to clear spans, don't block client process thread. TODO to use lock/notify
        BATCH_TRACE_LIST.get(previous).clear();
        return Json.encode(wrongTraceMap);
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
     *
     * @param badTraceIdList
     * @param batchPos
     */
    private void updateWrongTraceId(Set<String> badTraceIdList, int batchPos) {
        if (badTraceIdList.size() > 0) {
            JsonObject jsonBody = new JsonObject();
            jsonBody.put("badTraceIdList", new ArrayList<>(badTraceIdList));
            jsonBody.put("batchPos", batchPos);

            IAtomicLong atomicLong = VertxInstanceService.getHazelcastInstance().getCPSubsystem().getAtomicLong("sender");
            atomicLong.getAndIncrement();
            //GET BAD TRACE MD5
            vertx.eventBus().request("getMD5", jsonBody, handler ->{
                logger.info("getMD5 result:" + handler.succeeded());
            });


        }
    }

    private String getPath() {
        String port = System.getProperty("server.port", "8080");
        if ("8000".equals(port)) {
            return "http://localhost:" + CommonController.getDataSourcePort() + "/trace1.data";
//            return "/trace1.data";
        } else if ("8001".equals(port)) {
            return "http://localhost:" + CommonController.getDataSourcePort() + "/trace2.data";
//            return "/trace2.data";
        } else {
            return null;
        }
    }

}
