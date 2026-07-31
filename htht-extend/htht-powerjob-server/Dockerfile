#FROM findepi/graalvm:java17-native
FROM openjdk:11

MAINTAINER Lion Li

RUN mkdir -p /htht/powerjob/logs

WORKDIR /htht/powerjob

ENV LANG=C.UTF-8 LC_ALL=C.UTF-8 JAVA_OPTS="-Xms512m -Xmx1024m"

EXPOSE 7700

ADD ./target/htht-powerjob-server.jar ./app.jar

ENTRYPOINT java -Djava.security.egd=file:/dev/./urandom -jar app.jar \
           -XX:+HeapDumpOnOutOfMemoryError -Xlog:gc*,:time,tags,level -XX:+UseZGC ${JAVA_OPTS}
