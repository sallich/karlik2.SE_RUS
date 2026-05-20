# Roguelike + MCP (Kotlin)

Играбельная roguelike с MCP API для LLM-агентов.

## Модули

| Модуль | Порт | Назначение |
|--------|------|------------|
| `game-service` | 8080 | Игровой движок (state, карта, бой) |
| `mcp-server` | 8081 | MCP tools + JSON-RPC, прокси к game-service |
| `agent-runner` | 8082 | Agent loop, LLM, budget, логи tool calls |

Документация:

- [docs/game-design.md](docs/game-design.md) — механики, core loop, открытые вопросы
- [docs/game-engine.md](docs/game-engine.md) — движок на Kotlin, рендер, ассеты
- [docs/architecture.md](docs/architecture.md) — сервисы и деплой
- [docs/mcp-contract.json](docs/mcp-contract.json) — контракт MCP для агентов

## Локальная разработка

Требования: JDK 21 (или любая JDK 17+ — Gradle скачает toolchain 21 автоматически через Foojay).

```bash
./gradlew check          # тесты + detekt + JaCoCo (порог 10%)
./gradlew jacocoRootReport
./gradlew :game-service:run
```

## Docker

```bash
docker compose up --build
```

- http://localhost:8080/health — game-service
- http://localhost:8081/health — mcp-server (проверяет game-service)
- http://localhost:8082/health — agent-runner

MCP HTTP (для отладки): `GET http://localhost:8081/mcp/tools`, `POST http://localhost:8081/mcp/tools/call`.

## Переменные окружения

| Переменная | Сервис | Описание |
|------------|--------|----------|
| `GAME_SERVICE_URL` | mcp-server | URL game-service (по умолчанию `http://localhost:8080`) |
| `LLM_PROVIDER` | agent-runner | `stub`, `openai`, `anthropic`, … |
| `LLM_API_KEY` | agent-runner | Ключ провайдера |
| `MCP_TRANSPORT` | agent-runner | `stdio` (целевой) |
| `MCP_SERVER_COMMAND` | agent-runner | Команда запуска mcp-server для stdio MCP |

## CI

GitHub Actions: `./gradlew check jacocoRootReport` на push/PR в `main`/`master`.
