FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
RUN apk add --no-cache bash
COPY . .
RUN chmod +x gradlew && ./gradlew :agent-runner:installDist --no-daemon -x test

FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache wget
WORKDIR /app
COPY --from=build /workspace/agent-runner/build/install/agent-runner /app
ENV PORT=8082
EXPOSE 8082
CMD ["/app/bin/agent-runner"]
