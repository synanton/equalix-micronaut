# syntax=docker/dockerfile:1
# Equalix Micronaut runtime image. Two stages keep the shipping layer to JRE + app jar
# so image-size comparisons against equalix (Spring Boot) and equalix-go stay honest:
# same workload, same schema, same JDK baseline (eclipse-temurin:21-jre).

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -q -DskipTests dependency:go-offline
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/target/equalix-micronaut-*.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
