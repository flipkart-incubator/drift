#BASE IMAGE - extends public drift-worker
FROM drift-worker:latest

# Create extensions directory
RUN mkdir -p /usr/share/drift-worker/extensions

# Copy Flipkart-specific extension JAR
# This JAR contains BOTH implementations AND META-INF/services registrations
COPY target/worker-flipkart-*.jar /usr/share/drift-worker/extensions/worker-flipkart.jar

# Copy custom scripts (these will be available in the image)
COPY src/main/resources/scripts/* /src/main/resources/scripts/

# The base image already has ENTRYPOINT configured
# Classpath order ensures extension SPI registrations are discovered first:
# 1. /usr/share/drift-worker/extensions/* (Flipkart implementations + SPI registrations)
# 2. /usr/share/drift-worker/service/worker.jar (Base worker + any NoOp registrations)
#
# ServiceLoader.load() will find Flipkart providers first and use them

