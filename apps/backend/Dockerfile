# syntax=docker/dockerfile:1.7

FROM maven:3.9.6-eclipse-temurin-21 AS builder
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21.0.2_13-jre AS runtime
WORKDIR /app
COPY --from=builder /workspace/target/*.jar app.jar
EXPOSE 5001 5002
ENTRYPOINT ["java", "-jar", "app.jar"]

# JVM 메모리 및 GC 최적화
ENV JAVA_OPTS="-Xms512m \
               -Xmx2g \
               -XX:+UseG1GC \
               -XX:MaxGCPauseMillis=200 \
               -XX:+UseStringDeduplication \
               -XX:+OptimizeStringConcat \
               -XX:+ParallelRefProcEnabled \
               -XX:+ExitOnOutOfMemoryError \
               -Djava.security.egd=file:/dev/./urandom \
               -Dfile.encoding=UTF-8"

COPY target/*.jar app.jar

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]