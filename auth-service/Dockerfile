# ---------------------------------------------------------------------------
# Le contexte de build est la RACINE DU DÉPÔT, pas ce répertoire : le module a besoin
# du pom parent, et Maven a besoin de tous les poms de modules pour bâtir le réacteur.
#   docker build -f auth-service/Dockerfile .
# ---------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace

# Copier d'abord les seuls poms : cette couche n'est reconstruite que lorsqu'une
# dépendance change, si bien que les modifications de source du quotidien ne
# retéléchargent pas les dépendances.
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

# Ne jamais s'exécuter en root : une évasion de conteneur ne doit pas partir de l'uid 0.
RUN addgroup -S app && adduser -S -G app app
WORKDIR /app

COPY --from=build /workspace/auth-service/target/*.jar app.jar
RUN chown app:app app.jar
USER app

EXPOSE 8081

# Respecter la limite mémoire du conteneur plutôt que de présumer la RAM de l'hôte.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseContainerSupport"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
