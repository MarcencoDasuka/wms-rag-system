FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S wmsgroup && adduser -S wmsuser -G wmsgroup && \
    mkdir -p /app/logs && chown -R wmsuser:wmsgroup /app

USER wmsuser

COPY --chown=wmsuser:wmsgroup target/wms-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
