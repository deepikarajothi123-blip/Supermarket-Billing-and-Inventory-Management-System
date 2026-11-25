# Build from the repository root. Main.java, pom.xml and this Dockerfile are all here.
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml Main.java application.properties application-render.properties ./
RUN mvn -B -DskipTests dependency:go-offline
RUN mvn -B -DskipTests -Pproduction package

FROM eclipse-temurin:21-jre
WORKDIR /app
ENV PORT=8080
COPY --from=build /workspace/target/supermarket-billing-system-1.0.0.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java -XX:MaxRAMPercentage=75.0 -Dserver.port=${PORT:-8080} -jar /app/app.jar"]
