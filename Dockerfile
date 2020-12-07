FROM registry.cn-hangzhou.aliyuncs.com/jiulong/jdk15:1.0

COPY ./target/vertx-scoring-1.0.0-SNAPSHOT-fat.jar /mlh-install-packages/test/jar/vertx-scoring-1.0.0-SNAPSHOT-fat.jar
COPY ./bin /mlh-install-packages/test/jar
WORKDIR /mlh-install-packages/test/jar

RUN chmod +x /mlh-install-packages/test/jar/start.sh

ENTRYPOINT ["/bin/bash", "/mlh-install-packages/test/jar/start.sh"]