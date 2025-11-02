FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY . /app
RUN mvn -pl consumer-service -am -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/consumer-service/target/consumer-service-*-SNAPSHOT.jar app.jar
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
