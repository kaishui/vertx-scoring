#!/bin/bash

if   [  $SERVER_PORT  ];
then
  java -Dserver.port=$SERVER_PORT -jar vertx-scoring-1.0.0-SNAPSHOT-fat.jar -cluster
else
  java -Dserver.port=8000 -jar vertx-scoring-1.0.0-SNAPSHOT-fat.jar -cluster
fi