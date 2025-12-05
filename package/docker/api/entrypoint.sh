JAVA_OPTS="--add-opens java.base/java.lang=ALL-UNNAMED \
               --add-opens java.base/java.util=ALL-UNNAMED \
               -Dcom.sun.management.jmxremote \
               -Dcom.sun.management.jmxremote.port=5503  \
               -Dcom.sun.management.jmxremote.rmi.port=5503 \
               -Dcom.sun.management.jmxremote.local.only=false \
               -Dcom.sun.management.jmxremote.authenticate=false \
               -Dcom.sun.management.jmxremote.ssl=false \
               -XX:+UseG1GC \
               -XX:+FlightRecorder \
               -Djava.net.preferIPv4Stack=true \
               -Xlog:gc*:/var/log/drift-api/gc.log:time,tags:filecount=3,filesize=100M \
               -Dfile.encoding=UTF-8 \
               -XX:-OmitStackTraceInFastThrow \
               -XX:+HeapDumpOnOutOfMemoryError  -XX:HeapDumpPath=/var/log/drift-api/heap_dump.hprof"

exec java $JAVA_OPTS -server -jar -Xms"${JVM_XMS}" -Xmx"${JVM_XMX}" /usr/share/drift-api/service/api.jar server /usr/share/drift-api/config/config.yaml
