package com.ali.scoring.service;

import com.ali.scoring.controller.CommonController;
import com.google.common.collect.*;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.IntSummaryStatistics;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TraceDataService implements Runnable {
    private Logger logger = LoggerFactory.getLogger(TraceDataService.class);

    private static ImmutableSetMultimap.Builder<String, String> allMap = ImmutableSetMultimap.<String, String>builder();
    private static ImmutableSet.Builder<String> badTraceIds = ImmutableSet.<String>builder();

    public TraceDataService() {
    }

    public static void start() {
        Thread t = new Thread(new TraceDataService(), "ProcessDataThread");
        t.start();
    }

    @Override
    public void run() {
        //load data
        String port = System.getProperty("server.port", "8080");
        if ("8000".equals(port) || "8001".equals(port)) {
            long startTime = System.nanoTime();
            try {
                //call http api to get trace data
                HttpRequest request = HttpRequest.newBuilder().uri(URI.create(getPath())).GET().build();
                //outputstream with lines
                HttpResponse.BodyHandler<Stream<String>> bodyHandler = HttpResponse.BodyHandlers.ofLines();
                HttpClient client = HttpClient.newHttpClient();
                CompletableFuture<HttpResponse<Stream<String>>> future = client.sendAsync(request, bodyHandler);
                future.thenApply(HttpResponse::body).whenComplete((inputStream, ex) -> {

                    //id length ?
                    System.out.println("id metric");
                    IntSummaryStatistics summaryStatistics = inputStream.mapToInt(trace -> trace.indexOf("|"))
                            .summaryStatistics();
                    System.out.println(summaryStatistics);
                    // error=1 start ?
//                    String errorFlag = new StringBuffer("error=1").reverse().toString();
//                    String httpNomarl = new StringBuffer("http.status_code=200").reverse().toString();
//                    String http3 = new StringBuffer("http.status_code=3").reverse().toString();
//                    String http5 = new StringBuffer("http.status_code=5").reverse().toString();
//                    String http4 = new StringBuffer("http.status_code=4").reverse().toString();
//                    ImmutableSet<String> flags = ImmutableSet.of(
//                            errorFlag, http3, http4, http5, httpNomarl
//                    );
//
//                    flags.parallelStream().forEach(flag ->{
//                        System.out.println("error code:" + flag);
//                        IntSummaryStatistics errorStat = inputStream.map(trace -> new StringBuffer(trace).reverse())
//                                .map(trace -> trace.indexOf(flag)).mapToInt(len -> len).summaryStatistics();
//
//                        System.out.println(errorStat);
//
//                    });

                    // http_stust
//                    Multimap<String, String> list = inputStream.collect(Multimaps.toMultimap(trace -> trace.substring(0, trace.indexOf("\\|")),
//                            val -> val, MultimapBuilder.treeKeys().arrayListValues()::build));
//                    System.out.println(list.keys().toString());
//                    logger.debug("list");
                });

            } catch (Exception e) {

            } finally {
                long endTime = System.nanoTime();
                System.out.println("time:" + (endTime - startTime));
            }
        }
    }


    private String extractLineData(String line) {
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
