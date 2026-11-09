FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY . .
RUN ./gradlew --no-daemon installDist

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/install/ktor-project/ ./
EXPOSE 8080
ENTRYPOINT ["/app/bin/ktor-project"]