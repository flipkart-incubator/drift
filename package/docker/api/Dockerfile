#BASE IMAGE
FROM jfrog.fkinternal.com/fk-base-images/openjdk/jdk:17.0.2-debian11.3-slim-20220527

RUN mkdir -p /usr/share/drift-api/service && \
    mkdir -p /var/log/drift-api && \
    chmod a+rw /var/log/drift-api && \
    mkdir -p /etc/default/megh	&& \
    apt-get update -y && \
    apt-get --yes --allow-unauthenticated install curl && \
    apt-get --yes --allow-unauthenticated install telnet && \
    apt-get --yes --allow-unauthenticated install procps && \
    echo Asia/Calcutta > /etc/timezones

COPY api/target/api-*.jar /usr/share/drift-api/service/api.jar
COPY Docker/api/entrypoint.sh /entrypoint.sh

EXPOSE 5500 5501 5503
ENTRYPOINT [ "sh", "/entrypoint.sh" ]
