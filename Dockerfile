FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /workspace/fraud-rule-engine

COPY fraud-rule-engine/.mvn/ .mvn/
COPY fraud-rule-engine/mvnw fraud-rule-engine/pom.xml ./
RUN chmod +x mvnw
RUN ./mvnw -B dependency:go-offline

COPY fraud-rule-engine/src/ src/
COPY contracts/ /workspace/contracts/
COPY monitoring/ /workspace/monitoring/
COPY docker/HealthCheck.java /workspace/docker/HealthCheck.java
RUN ./mvnw -B clean verify \
	&& mkdir -p /healthcheck \
	&& javac --release 21 -d /healthcheck ../docker/HealthCheck.java \
	&& cp target/fraud-rule-engine-0.0.1-SNAPSHOT.jar /workspace/app.jar

FROM eclipse-temurin:21.0.8_9-jre-noble AS runtime

WORKDIR /app

RUN groupadd --system spring \
	&& useradd --system --gid spring --home-dir /app spring

COPY --from=build --chown=spring:spring /workspace/app.jar /app/app.jar
COPY --from=build --chown=spring:spring /healthcheck /healthcheck

USER spring:spring

EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=3s --start-period=45s --retries=12 \
	CMD ["java", "-cp", "/healthcheck", "HealthCheck"]

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
