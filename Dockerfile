FROM eclipse-temurin:17-jdk AS build

WORKDIR /workspace
ENV GRADLE_USER_HOME=/workspace/.gradle

COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts settings.gradle.kts ./

RUN chmod +x gradlew

RUN ./gradlew dependencies --no-daemon

COPY src src

RUN ./gradlew clean bootJar --no-daemon \
  && find build/libs -name "*-plain.jar" -delete \
  && cp build/libs/*.jar /workspace/app.jar

FROM amazoncorretto:17-alpine AS runtime

WORKDIR /app

# 기본 시간대를 기존 Ubuntu 이미지와 같은 Etc/UTC 로 맞춘다.
# Alpine 은 /etc/localtime 이 없어 TZ 를 비우면 JVM 이 'GMT' 로 잡는다(오프셋은 같지만 존 ID 가 달라진다).
ENV TZ=Etc/UTC

COPY --from=build /workspace/app.jar /app/app.jar

EXPOSE 3579

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
