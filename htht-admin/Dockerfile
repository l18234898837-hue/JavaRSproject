FROM shallow-topography:server-back-v2

LABEL maintainer="MSFY <3292468635@qq.com>"

RUN mkdir -p /htht/server/logs \
    /htht/server/temp \
    /htht/skywalking/agent

WORKDIR /htht/server

ENV SERVER_PORT=8080
ENV TILE_SERVER_PORT=8888

EXPOSE ${SERVER_PORT}
EXPOSE ${TILE_SERVER_PORT}

ENTRYPOINT ["java", \
            "--add-opens", "java.base/java.lang=ALL-UNNAMED", \
            "--add-opens", "java.base/java.lang.invoke=ALL-UNNAMED", \
            "-Djasypt.pwd=password", \
            "-Djava.security.egd=file:/dev/./urandom", \
            "-Dserver.port=${SERVER_PORT}", \
            "-Dtile-server.port=${TILE_SERVER_PORT}", \
            # 应用名称 如果想区分集群节点监控 改成不同的名称即可
#            "-Dskywalking.agent.service_name=ruoyi-server", \
#            "-javaagent:/ruoyi/skywalking/agent/skywalking-agent.jar", \
            "-jar", "htht-admin.jar", \
            "--spring.profiles.active=prod"]

#不暴露端口，使用jar中的端口，此时镜像可以当作基础镜像，供其他项目使用
