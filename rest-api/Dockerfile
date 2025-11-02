# Build stage
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY . /app
RUN mvn -pl rest-api -am -DskipTests package

# Runtime stage
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/rest-api/target/rest-api-1.0-SNAPSHOT.jar app.jar
ENV REST_PORT="7000"
ENV GRPC_TARGET="grpc-service:50051"
EXPOSE 7000
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
