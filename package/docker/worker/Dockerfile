#BASE IMAGE
FROM jfrog.fkinternal.com/fk-base-images/openjdk/jdk:17.0.2-debian11.3-slim-20220527

RUN mkdir -p /usr/share/drift-worker/service && \
    mkdir -p /var/log/drift-worker && \
    mkdir -p /src/main/resources/scripts && \
    chmod a+rw /var/log/drift-worker && \
    mkdir -p /etc/default/megh	&& \
    apt-get update -y && \
    apt-get --yes --allow-unauthenticated install curl && \
    apt-get --yes --allow-unauthenticated install telnet && \
    apt-get --yes --allow-unauthenticated install procps && \
    echo Asia/Calcutta > /etc/timezones

COPY worker/target/worker-*.jar /usr/share/drift-worker/service/worker.jar
COPY package/docker/worker/entrypoint.sh /entrypoint.sh

# Default values for JVM settings
ENV JVM_XMS="8g"
ENV JVM_XMX="8g"
EXPOSE 5600 5601 5603
ENTRYPOINT [ "sh", "/entrypoint.sh" ]