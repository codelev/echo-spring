FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /java
COPY . .
RUN mvn -B -e -C -T 1C org.apache.maven.plugins:maven-dependency-plugin:3.0.2:go-offline
RUN mvn clean package

FROM eclipse-temurin:21-jre
COPY --from=build /java/target/app.jar app.jar
EXPOSE 9000
ENTRYPOINT ["sh", "-c", "ulimit -n 10000 && exec java -jar app.jar"]