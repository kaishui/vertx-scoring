package com.ali.scoring.service;

import com.ali.scoring.controller.CommonController;
import com.hazelcast.collection.ISet;
import com.hazelcast.multimap.MultiMap;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class TraceDataService implements Runnable {
    private Logger logger = LoggerFactory.getLogger(TraceDataService.class);

    public TraceDataService() {
    }

    public static void start() {
        Thread t = new Thread(new TraceDataService(), "ProcessDataThread");
        t.start();
    }

    @Override
    public void run() {
        //Todo: load data
        String port = System.getProperty("server.port", "8080");
        if ("8000".equals(port) || "8001".equals(port)) {

            try {
               URL url = new URL(getPath());
                HttpURLConnection httpConnection = (HttpURLConnection) url.openConnection(Proxy.NO_PROXY);
                InputStream input = httpConnection.getInputStream();
                BufferedReader bf = new BufferedReader(new InputStreamReader(input));
                MultiMap<Object, Object> allMap = VertxInstanceService.getHazelcastInstance().getMultiMap("all");
                ISet<Object> badTraceIds = VertxInstanceService.getHazelcastInstance().getSet("badTraceIds");
                long startTime = System.nanoTime();
                String line = null;
                try {
                    while ((line = bf.readLine()) != null) {
//                        CompletableFuture<String> future = new CompletableFuture<>();
//                        future.complete(line);
//                        future.thenCompose(lineStr -> {
//                            CompletableFuture<String> next = new CompletableFuture<>();
//                            extractLineData(lineStr, allMap, badTraceIds, traceId ->{
//                                next.complete(traceId);
//                            });
//                            return next;
//                        }).whenComplete((value, ex) ->{
//                            logger.debug("traceId:{}" + value);
//                        });

                    }
                } catch (Exception e) {

                } finally {
                    long endTime = System.nanoTime();
                    System.out.println("time:" + (endTime - startTime));



                    //TODO: COUNT checksum
                    //
                }

            } catch (Exception e) {
            }

//            WebClient.create(VertxInstanceService.getVertx())
//                    .get(Integer.parseInt(CommonController.getDataSourcePort()), "localhost", getURI())
//                    .send().onSuccess(this::handle);
        }
    }

    private void handle(HttpResponse<Buffer> res) {
        System.out.println(res.statusCode());
        Buffer buffer = res.bodyAsBuffer();
        InputStream inputStream = new ByteArrayInputStream(buffer.getBytes());
        BufferedReader bf = new BufferedReader(new InputStreamReader(inputStream));

        MultiMap<Object, Object> allMap = VertxInstanceService.getHazelcastInstance().getMultiMap("all");
        ISet<Object> badTraceIds = VertxInstanceService.getHazelcastInstance().getSet("badTraceIds");
        long startTime = System.nanoTime();
        String line = null;
        try {
            while ((line = bf.readLine()) != null) {
//                extractLineData(line, allMap, badTraceIds);
            }
        } catch (Exception e) {

        } finally {
            long endTime = System.nanoTime();
            System.out.println("time:" + (endTime - startTime));
            //TODO: COUNT checksum
            //
        }
    }

    private String extractLineData(String line, MultiMap<Object, Object> allMap, ISet<Object> badTraceIds, Consumer<String> result) {
        String[] cols = line.split("\\|");
        String traceId = null;
        if (cols != null && cols.length > 1) {
            traceId = cols[0];
            allMap.put(traceId, line);
            if (cols.length > 8) {
                String tags = cols[8];
                if (tags != null) {
                    // 错误数据设置在 badTraceIdList
                    if (tags.contains("error=1")) {
                        //todo: listener
                        badTraceIds.add(traceId);
                    } else if (tags.contains("http.status_code=") && tags.indexOf("http.status_code=200") < 0) {
                        badTraceIds.add(traceId);
                    }
                }
            }
        }
        result.accept(traceId);
        return traceId;
    }

    private String getURI() {
        String port = System.getProperty("server.port", "8080");
        if ("8000".equals(port)) {
//            return "http://localhost:" + CommonController.getDataSourcePort() + "/trace1.data";
            return "/trace1.data";
        } else if ("8001".equals(port)) {
//            return "http://localhost:" + CommonController.getDataSourcePort() + "/trace2.data";
            return "/trace2.data";
        } else {
            return null;
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
