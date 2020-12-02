FROM centos:centos7


#1. set java_home
COPY /software/path/jdk-15.0.1 /software/path/jdk-15.0.1

COPY ./target/vertx-scoring-1.0.0-SNAPSHOT-fat.jar /mlh-install-packages/test/jar/vertx-scoring-1.0.0-SNAPSHOT-fat.jar
COPY ./bin /mlh-install-packages/test/jar

ENV JAVA_HOME /software/path/jdk-15.0.1

ENV PATH $PATH:$JAVA_HOME/bin
WORKDIR /mlh-install-packages/test/jar

RUN ls -l

RUN chmod +x /mlh-install-packages/test/jar/start.sh

ENTRYPOINT ["/bin/bash", "/mlh-install-packages/test/jar/start.sh"]