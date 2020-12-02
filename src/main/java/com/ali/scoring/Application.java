package com.ali.scoring;

import com.ali.scoring.service.VertxInstanceService;
import com.ali.scoring.vertx_scoring.MainVerticle;
import com.hazelcast.config.Config;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.spi.cluster.hazelcast.ConfigUtil;
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;

public class Application {

    public static void main(String[] args) throws Exception {
        Config hazelcastConfig = ConfigUtil.loadConfig();

        hazelcastConfig.setClusterName("my-cluster-name");

        ClusterManager mgr = new HazelcastClusterManager(hazelcastConfig);

        VertxOptions options = new VertxOptions().setClusterManager(mgr);


        Vertx.clusteredVertx(options, res -> {
            if (res.succeeded()) {
                Vertx vertx = res.result();
                VertxInstanceService.setVertx(vertx);
                vertx.deployVerticle(new MainVerticle());
            } else {
                // failed!
            }
        });
    }
}
