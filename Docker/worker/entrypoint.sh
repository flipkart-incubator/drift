JVM_OPTS="--add-opens java.base/java.lang=ALL-UNNAMED \
          -Dcom.sun.management.jmxremote \
          -Dcom.sun.management.jmxremote.port=5603  \
          -Dcom.sun.management.jmxremote.rmi.port=5603 \
          -Dcom.sun.management.jmxremote.local.only=false \
          -Dcom.sun.management.jmxremote.authenticate=false \
          -Dcom.sun.management.jmxremote.ssl=false \
          -Dgroovy.use.classvalue=true \
          -XX:+UseG1GC \
          -XX:+FlightRecorder \
          -Djava.net.preferIPv4Stack=true \
          -Xlog:gc*:/var/log/drift-worker/gc.log:time,tags:filecount=3,filesize=100M \
          -Dfile.encoding=UTF-8 \
          -XX:-OmitStackTraceInFastThrow \
          -XX:+HeapDumpOnOutOfMemoryError  -XX:HeapDumpPath=/var/log/drift-worker/heap_dump.hprof"

exec java "$JVM_OPTS" -server -jar -Xms"${JVM_XMS}" -Xmx"${JVM_XMX}" /usr/share/drift-worker/service/worker.jar server /usr/share/drift-worker/config/config.yaml