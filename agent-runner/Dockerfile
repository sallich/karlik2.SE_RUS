FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
RUN apk add --no-cache bash
COPY . .
RUN chmod +x gradlew && ./gradlew :agent-runner:installDist :mcp-server:installDist --no-daemon -x test

FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache wget
WORKDIR /app
COPY --from=build /workspace/agent-runner/build/install/agent-runner /app
COPY --from=build /workspace/mcp-server/build/install/mcp-server /opt/mcp-server
ENV PORT=8082
ENV MCP_SERVER_COMMAND="/opt/mcp-server/bin/mcp-server stdio"
ENV LLM_PROVIDER=heuristic
EXPOSE 8082
CMD ["/app/bin/agent-runner"]
