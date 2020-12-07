#!/bin/bash

if   [  $SERVER_PORT  ];
then
  if [ 8000 == $SERVER_PORT ] || [ 8001 == $SERVER_PORT ]
    java -Dserver.port=$SERVER_PORT -XX:+UnlockExperimentalVMOptions -XX:+UseZGC -Xmx4G -Xms4G -XX:NewRatio=9 -XX:GCTimeRatio=99 -jar vertx-scoring-1.0.0-SNAPSHOT-fat.jar -cluster
  else
    java -Dserver.port=$SERVER_PORT -XX:+UnlockExperimentalVMOptions -XX:+UseZGC -Xmx2G -Xms2G -XX:NewRatio=9 -XX:GCTimeRatio=99 -jar vertx-scoring-1.0.0-SNAPSHOT-fat.jar -cluster
  fi
else
  java -Dserver.port=8000 -XX:+UnlockExperimentalVMOptions -XX:+UseZGC -Xmx4G -Xms4G -XX:NewRatio=9 -XX:GCTimeRatio=99 -jar vertx-scoring-1.0.0-SNAPSHOT-fat.jar -cluster
fi