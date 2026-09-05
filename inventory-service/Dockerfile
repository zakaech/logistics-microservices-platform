# ---------------------------------------------------------------------------
# Le contexte de build est la RACINE DU DÉPÔT :
#   docker build -f inventory-service/Dockerfile .
# ---------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace

COPY pom.xml ./
COPY auth-service/pom.xml auth-service/pom.xml
COPY catalog-service/pom.xml catalog-service/pom.xml
COPY inventory-service/pom.xml inventory-service/pom.xml
COPY order-service/pom.xml order-service/pom.xml
COPY api-gateway/pom.xml api-gateway/pom.xml
RUN mvn -B -q -pl inventory-service -am dependency:go-offline

COPY inventory-service/src inventory-service/src
RUN mvn -B -q -pl inventory-service -am clean package -DskipTests

# ---------------------------------------------------------------------------
FROM eclipse-temurin:17-jre-alpine AS runtime

RUN addgroup -S app && adduser -S -G app app
WORKDIR /app

COPY --from=build /workspace/inventory-service/target/*.jar app.jar
RUN chown app:app app.jar
USER app

EXPOSE 8083

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseContainerSupport"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
