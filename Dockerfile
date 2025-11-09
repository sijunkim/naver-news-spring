FROM eclipse-temurin:17-jdk AS build

WORKDIR /workspace
ENV GRADLE_USER_HOME=/workspace/.gradle

COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts settings.gradle.kts ./
COPY src src

RUN chmod +x gradlew

RUN ./gradlew clean bootJar --no-daemon \
  && find build/libs -name "*-plain.jar" -delete \
  && cp build/libs/*.jar /workspace/app.jar

FROM eclipse-temurin:17-jre-jammy AS runtime

WORKDIR /app

COPY --from=build /workspace/app.jar /app/app.jar

EXPOSE 3579

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
