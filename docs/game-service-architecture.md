# Архитектура game-service (движок)

## Слои

```text
api/              HTTP (Ktor), только DTO
application/      GameEngine — сессии, фасад для API
domain/           правила без фреймворков
  session/        GameSession, RoomEngagementSystem
  command/        Command, CommandDispatcher, CommandRegistry
  phase/          State: PhaseHandler, PhaseRegistry
  event/          Observer: GameEvent, GameEventBus
  combat/         бой, мобы, урон
  ai/             Strategy: MobBehavior
  level/          LevelGenerator (порт)
infrastructure/   адаптеры: TestLevelGenerator, LevelGeneratorFactory, AgentRunnerMobClient
```

## Паттерны

| Паттерн | Где |
|---------|-----|
| **Command** | `SyncInputCommand`, `LegacyMovementCommand`, `CommandRegistry`, `CommandDispatcher` |
| **State** | `PhaseHandler`, `SessionPhase`, `PhaseRegistry` |
| **Strategy** | `MobBehavior` — `RusherBehavior`, `ShooterBehavior`, `LlmGuardBehavior` |
| **Factory** | `LevelGeneratorFactory` |
| **Observer** | `GameEventBus`, `GameEventListener` |

## Поток запроса

```mermaid
sequenceDiagram
    participant API as GameRoutes
    participant App as GameEngine
    participant Disp as CommandDispatcher
    participant Phase as PhaseHandler
    participant Cmd as GameCommand
    participant Bus as GameEventBus

    API->>App: syncInput / applyAction
    App->>Disp: dispatch(session, command)
    Disp->>Phase: validateCommand
    Disp->>Cmd: validate + execute
    Disp->>Bus: publish(events)
    App-->>API: GameSnapshot
```

## Расширение

- Новое действие: класс `GameCommand` + регистрация в `CommandRegistry.defaultBuilder()`.
- Новая фаза: `PhaseHandler` + запись в `PhaseRegistry.defaultHandlers()`.
- Процген: `LevelGenerator` + ветка в `LevelGeneratorFactory`.
- Новый тип моба: реализация `MobBehavior` + wiring в `MobSpawner` / `CombatSystem`.

`shared` — DTO, `TileMap`, `SessionPhase`, FPS-движение, протокол MCP; общие типы агентов (`AgentConfig`, MCP tool schemas) — для `agent-runner` и `policy-agent-runner`.
