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

# Build classpath with extensions taking precedence for SPI discovery
CLASSPATH=""

# 1. Add extensions FIRST (if present) - their META-INF/services will be discovered before base worker
if [ -d "/usr/share/drift-worker/extensions" ] && [ -n "$(ls -A /usr/share/drift-worker/extensions/*.jar 2>/dev/null)" ]; then
    CLASSPATH="/usr/share/drift-worker/extensions/*"
fi

# 2. Add base worker JAR
if [ -z "$CLASSPATH" ]; then
    CLASSPATH="/usr/share/drift-worker/service/worker.jar"
else
    CLASSPATH="$CLASSPATH:/usr/share/drift-worker/service/worker.jar"
fi

exec java $JVM_OPTS -server -cp "$CLASSPATH" -Xms"${JVM_XMS}" -Xmx"${JVM_XMX}" com.flipkart.drift.worker.bootstrap.WorkerApplication server /usr/share/drift-worker/config/config.yaml
