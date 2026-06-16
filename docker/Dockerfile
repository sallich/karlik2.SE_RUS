# syntax=docker/dockerfile:1.4
# Одна Gradle-сборка для всех Kotlin-сервисов (BuildKit кэширует этот stage).
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
RUN apk add --no-cache bash
COPY . .

ENV GRADLE_OPTS="-Dorg.gradle.daemon=false -Dorg.gradle.parallel=false"
RUN --mount=type=cache,target=/root/.gradle/caches \
    --mount=type=cache,target=/root/.gradle/wrapper \
    chmod +x gradlew && \
    ./gradlew \
      :game-service:installDist \
      :mcp-server:installDist \
      :agent-runner:installDist \
      :policy-agent-runner:installDist \
      --no-daemon -x test \
      -Dorg.gradle.jvmargs=-Xmx768m

FROM eclipse-temurin:21-jre-alpine AS game-service
RUN apk add --no-cache wget
WORKDIR /app
COPY --from=build /workspace/game-service/build/install/game-service /app
ENV PORT=8080
EXPOSE 8080
CMD ["/app/bin/game-service"]

FROM eclipse-temurin:21-jre-alpine AS mcp-server
RUN apk add --no-cache wget
WORKDIR /app
COPY --from=build /workspace/mcp-server/build/install/mcp-server /app
ENV PORT=8081
ENV GAME_SERVICE_URL=http://game-service:8080
EXPOSE 8081
CMD ["/app/bin/mcp-server"]

FROM eclipse-temurin:21-jre-alpine AS agent-runner
RUN apk add --no-cache wget
WORKDIR /app
COPY --from=build /workspace/agent-runner/build/install/agent-runner /app
COPY --from=build /workspace/mcp-server/build/install/mcp-server /opt/mcp-server
ENV PORT=8082
ENV MCP_SERVER_COMMAND="/opt/mcp-server/bin/mcp-server stdio"
EXPOSE 8082
CMD ["/app/bin/agent-runner"]

FROM eclipse-temurin:21-jre-alpine AS policy-agent-runner
RUN apk add --no-cache wget
WORKDIR /app
COPY --from=build /workspace/policy-agent-runner/build/install/policy-agent-runner /app
COPY --from=build /workspace/mcp-server/build/install/mcp-server /opt/mcp-server
ENV PORT=8083
ENV MCP_SERVER_COMMAND="/opt/mcp-server/bin/mcp-server stdio"
EXPOSE 8083
CMD ["/app/bin/policy-agent-runner"]
