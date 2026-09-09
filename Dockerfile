FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 taxiagent
WORKDIR /app
COPY --from=build /workspace/target/*.jar app.jar
USER taxiagent
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

