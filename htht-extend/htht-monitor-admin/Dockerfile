FROM openjdk:11

MAINTAINER Lion Li

RUN mkdir -p /htht/monitor/logs

WORKDIR /htht/monitor

EXPOSE 9090

ADD ./target/htht-monitor-admin.jar ./app.jar

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
