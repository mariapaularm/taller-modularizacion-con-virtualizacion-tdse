FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY pom.xml ./
COPY .mvn ./.mvn
COPY mvnw ./
COPY mvnw.cmd ./
COPY src ./src

RUN mvn -B clean package -DskipTests && mkdir -p target/dependency

FROM eclipse-temurin:21-jre

WORKDIR /app

ENV SERVER_PORT=8080

COPY --from=build /workspace/target/classes /app/classes
COPY --from=build /workspace/target/dependency /app/dependency

EXPOSE 8080

CMD ["java", "-cp", "/app/classes:/app/dependency/*", "com.example.demo.app.ExampleApp"]
