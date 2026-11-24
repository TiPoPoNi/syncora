FROM eclipse-temurin:21-jdk
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN apt-get update && apt-get install -y maven && mvn -q -DskipTests package
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "target/project-resource-scheduling-system-0.0.1-SNAPSHOT.jar"]
