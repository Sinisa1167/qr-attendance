# ---------- 1. faza: build ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src

# pom prvo, da se zavisnosti kesiraju dok se pom.xml ne promijeni
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline

COPY src ./src
# -DskipTests: contextLoads test traži config-server, kojeg u build-u nema
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests package \
    && cp target/*.jar /app.jar

# ---------- 2. faza: runtime ----------
FROM eclipse-temurin:17-jdk-alpine
RUN apk add --no-cache font-dejavu \
    && addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /app.jar app.jar
USER app
ENTRYPOINT ["java", "-Duser.timezone=Europe/Sarajevo", "-jar", "app.jar"]
