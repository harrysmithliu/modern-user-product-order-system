FROM maven:3.9.9-eclipse-temurin-17 AS build

WORKDIR /workspace

COPY services/order-service/pom.xml ./services/order-service/pom.xml
COPY services/order-service/src ./services/order-service/src

WORKDIR /workspace/services/order-service
RUN mvn -DskipTests package

FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

COPY --from=build /workspace/services/order-service/target/order-service-0.1.0.jar /app/app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
