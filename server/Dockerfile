# Two-stage build: Gradle's `application` plugin installDist produces a runnable dir
# (start script + jars, no fat-jar/shadow plugin needed), copied into a slim JRE runtime.
FROM amazoncorretto:25 AS build
WORKDIR /workspace
COPY . .
RUN chmod +x ./gradlew && ./gradlew :server:installDist --no-daemon

FROM amazoncorretto:25
WORKDIR /app
COPY --from=build /workspace/server/build/install/server ./
EXPOSE 8080
ENTRYPOINT ["./bin/server"]
