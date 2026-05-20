FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
RUN apk add --no-cache bash
COPY . .
RUN chmod +x gradlew && ./gradlew :game-service:installDist --no-daemon -x test

FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache wget
WORKDIR /app
COPY --from=build /workspace/game-service/build/install/game-service /app
ENV PORT=8080
EXPOSE 8080
CMD ["/app/bin/game-service"]
