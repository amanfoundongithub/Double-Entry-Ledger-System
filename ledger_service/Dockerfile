FROM eclipse-temurin:25-jdk AS build

WORKDIR /workspace

COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
COPY src ./src

RUN chmod +x ./gradlew && ./gradlew --no-daemon bootJar

FROM eclipse-temurin:25-jre

WORKDIR /app
RUN mkdir -p /app && chown 10001:10001 /app
COPY --from=build --chown=10001:10001 /workspace/build/libs/*.jar /app/app.jar

ENV SPRING_PROFILES_ACTIVE=container

USER 10001:10001
EXPOSE 5555

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
