FROM eclipse-temurin:21-jdk AS build

WORKDIR /app
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
COPY src ./src
RUN chmod +x gradlew && ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre

ENV PORT=55888
ENV DATABASE_URL="jdbc:h2:mem:tickets;DB_CLOSE_DELAY=-1;MODE=PostgreSQL"

WORKDIR /app
COPY --from=build /app/build/libs/ticket-center-api-1.0.0.jar app.jar

EXPOSE 55888
ENTRYPOINT ["java", "-jar", "app.jar"]
