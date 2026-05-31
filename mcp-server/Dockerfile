FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
RUN apk add --no-cache bash
COPY . .
RUN chmod +x gradlew && ./gradlew :mcp-server:installDist --no-daemon -x test

FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache wget
WORKDIR /app
COPY --from=build /workspace/mcp-server/build/install/mcp-server /app
ENV PORT=8081
ENV GAME_SERVICE_URL=http://game-service:8080
EXPOSE 8081
CMD ["/app/bin/mcp-server"]
