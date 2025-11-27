FROM gradle:8.5-jdk17 AS builder
WORKDIR /home/gradle/src
COPY build.gradle.kts settings.gradle.kts ./
COPY src ./src
# Refresh dependencies to avoid stale cached jjwt versions and build the jar
RUN gradle --refresh-dependencies clean bootJar --no-daemon

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=builder /home/gradle/src/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
