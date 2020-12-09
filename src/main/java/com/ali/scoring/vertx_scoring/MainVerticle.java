package com.ali.scoring.vertx_scoring;

import com.ali.scoring.config.Utils;
import com.ali.scoring.controller.CommonController;
import com.ali.scoring.service.CheckSumService;
import com.ali.scoring.service.TraceDataService;
import com.ali.scoring.service.VertxInstanceService;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.ext.web.Router;


public class MainVerticle extends AbstractVerticle {

    @Override
    public void start(Promise<Void> startPromise) throws Exception {
        int port = Integer.parseInt(System.getProperty("server.port", "8000"));
        VertxInstanceService.setVertx(vertx);
        Router router = CommonController.router(vertx);
        if (Utils.isBackendProcess()) {
            CheckSumService.start(vertx);
        }

        if (Utils.isClientProcess()) {
            TraceDataService.init();
        }

        VertxInstanceService.setVertx(vertx);
        vertx.createHttpServer().requestHandler(router).listen(port, http -> {
            if (http.succeeded()) {
                startPromise.complete();
                System.out.println("HTTP server started on port" + port);
            } else {
                startPromise.fail(http.cause());
            }
        });
    }

}
