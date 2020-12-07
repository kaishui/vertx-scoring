package com.ali.scoring.controller;

import com.ali.scoring.config.Constants;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;

import java.util.ArrayList;
import java.util.List;

public class BackendController {
    private static final Logger LOGGER = LoggerFactory.getLogger(BackendController.class.getName());


    // FINISH_PROCESS_COUNT will add one, when process call finish();
    private static volatile Integer FINISH_PROCESS_COUNT = 0;

    // single thread to run, do not use lock
    private static volatile Integer CURRENT_BATCH = 0;

    // save 90 batch for wrong trace
    private static int BATCH_COUNT = 90;

    //保存90 batch list
    private static List<TraceIdBatch> TRACEID_BATCH_LIST= new ArrayList<>();
    public static  void init() {
        for (int i = 0; i < BATCH_COUNT; i++) {
            TRACEID_BATCH_LIST.add(new TraceIdBatch());
        }
    }

    public static void router(Router router) {
        router.route("/setWrongTraceId").handler(ctx -> {
            JsonObject body = ctx.getBodyAsJson();
            int batchPos = body.getInteger("batchPos");
            int pos = body.getInteger("batchPos") % BATCH_COUNT;
            List<String> traceIdList = body.getJsonArray("badTraceIdList").getList();

            TraceIdBatch traceIdBatch = TRACEID_BATCH_LIST.get(pos);
            if (traceIdBatch.getBatchPos() != 0 && traceIdBatch.getBatchPos() != batchPos) {
                LOGGER.warn("overwrite traceId batch when call setWrongTraceId");
            }

            if (traceIdList != null && traceIdList.size() > 0) {
                traceIdBatch.setBatchPos(batchPos);
                traceIdBatch.setProcessCount(traceIdBatch.getProcessCount() + 1);
                traceIdBatch.getTraceIdList().addAll(traceIdList);
            }

            //            TraceDataService.start();
            HttpServerResponse response = ctx.response();
            // Write to the response and end it
            response.end("suc");
        });

        router.get("/finish").handler(ctx -> {

            FINISH_PROCESS_COUNT++;
            HttpServerResponse response = ctx.response();

            // Write to the response and end it
            response.end("suc");
        });


    }


    /**
     * trace batch will be finished, when client process has finished.(FINISH_PROCESS_COUNT == PROCESS_COUNT)
     * @return
     */
    public static boolean isFinished() {
        for (int i = 0; i < BATCH_COUNT; i++) {
            TraceIdBatch currentBatch = TRACEID_BATCH_LIST.get(i);
            if (currentBatch.getBatchPos() != 0) {
                return false;
            }
        }
        if (FINISH_PROCESS_COUNT < Constants.PROCESS_COUNT) {
            return false;
        }
        return true;
    }

    /**
     * get finished bath when current and next batch has all finished
     * @return
     */
    public static TraceIdBatch getFinishedBatch() {
        int next = CURRENT_BATCH + 1;
        if (next >= BATCH_COUNT) {
            next = 0;
        }
        TraceIdBatch nextBatch = TRACEID_BATCH_LIST.get(next);
        TraceIdBatch currentBatch = TRACEID_BATCH_LIST.get(CURRENT_BATCH);
        // when client process is finished, or then next trace batch is finished. to get checksum for wrong traces.
        if ((FINISH_PROCESS_COUNT >= Constants.PROCESS_COUNT && currentBatch.getBatchPos() > 0) ||
                (nextBatch.getProcessCount() >= Constants.PROCESS_COUNT && currentBatch.getProcessCount() >= Constants.PROCESS_COUNT)) {
            // reset
            TraceIdBatch newTraceIdBatch = new TraceIdBatch();
            TRACEID_BATCH_LIST.set(CURRENT_BATCH, newTraceIdBatch);
            CURRENT_BATCH = next;
            return currentBatch;
        }

        return null;
    }
}
