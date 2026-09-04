# ---------------------------------------------------------------------------
# Build context is the REPOSITORY ROOT, not this directory: the module needs the
# parent pom, and Maven needs every module pom to build the reactor.
#   docker build -f auth-service/Dockerfile .
# ---------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace

# Copy only the poms first: this layer is rebuilt only when a dependency
# changes, so day-to-day source edits do not re-download the world.
COPY pom.xml ./
COPY auth-service/pom.xml auth-service/pom.xml
COPY catalog-service/pom.xml catalog-service/pom.xml
COPY inventory-service/pom.xml inventory-service/pom.xml
COPY order-service/pom.xml order-service/pom.xml
COPY api-gateway/pom.xml api-gateway/pom.xml
RUN mvn -B -q -pl auth-service -am dependency:go-offline

COPY auth-service/src auth-service/src
RUN mvn -B -q -pl auth-service -am clean package -DskipTests

# ---------------------------------------------------------------------------
FROM eclipse-temurin:17-jre-alpine AS runtime

# Never run as root: a container escape should not start from uid 0.
RUN addgroup -S app && adduser -S -G app app
WORKDIR /app

COPY --from=build /workspace/auth-service/target/*.jar app.jar
RUN chown app:app app.jar
USER app

EXPOSE 8081

# Respect the container's memory limit instead of assuming the host's RAM.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseContainerSupport"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
