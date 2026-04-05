FROM maven:3.9-amazoncorretto-21 AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests -Dmaven.compiler.source=21 -Dmaven.compiler.target=21

FROM amazoncorretto:21
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
