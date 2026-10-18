# Stage 1: Build file .jar bằng Maven
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Môi trường chạy (Runtime) siêu nhẹ
FROM eclipse-temurin:21-jdk-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# Mở port 8080 cho Render
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]