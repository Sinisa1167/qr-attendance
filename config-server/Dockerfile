FROM eclipse-temurin:17-jdk-alpine
WORKDIR /app
COPY target/*.jar app.jar
ENTRYPOINT ["java", "-Duser.timezone=Europe/Sarajevo", "-jar", "app.jar"]