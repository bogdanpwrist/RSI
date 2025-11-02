# Build stage
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY . /app
RUN mvn -pl grpc-service -am -DskipTests package

# Runtime stage
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/grpc-service/target/grpc-service-1.0-SNAPSHOT.jar app.jar
ENV GRPC_PORT=50051
ENV STORAGE_DIR=/data/storage
VOLUME /data/storage
EXPOSE 50051
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
