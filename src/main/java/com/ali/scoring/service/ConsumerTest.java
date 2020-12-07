package com.ali.scoring.service;

import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.json.JsonObject;


public class ConsumerTest implements Runnable {

    private Logger logger = LoggerFactory.getLogger(ConsumerTest.class);

    public static void start() {
        Thread t = new Thread(new ConsumerTest(), "ConsumerTest");
        t.start();
    }

    @Override
    public void run() {
        VertxInstanceService.getVertx().eventBus().consumer("send-to-test", consumer ->{
            JsonObject body = (JsonObject) consumer.body();
            logger.debug(consumer.body());

        });
    }
}
