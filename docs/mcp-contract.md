# MCP Contract — Roguelike Agent

Агент (`agent-runner`) взаимодействует с игрой **только** через MCP-сервер. Прямой HTTP к `game-service` запрещён.

## Transport

| Режим | Когда |
|-------|--------|
| **stdio** (primary) | Локально и в Docker: `java -jar mcp-server.jar stdio` |
| **HTTP bridge** | Интеграционные тесты: `GET /mcp/tools`, `POST /mcp/tools/call` |

JSON-RPC 2.0, по одному сообщению на строку (newline-delimited).

## Tools (6)

### `game_new_session`

Создаёт сессию.

```json
{ "seed": 42, "twoLevel": false }
```

Ответ — полный `GameSnapshot` с `sessionId`.

### `game_observe`

```json
{ "sessionId": "abc-123" }
```

Полный снимок: карта, позиция, ключи, мобы, `phase`.

### `game_act`

Дискретное действие агента:

| action | Эффект |
|--------|--------|
| `move_forward` | ~1 клетка вперёд |
| `turn_left` / `turn_right` | поворот |
| `interact` | подбор ключа / выход через ворота |
| `wait` | пауза |
| `move_north/south/east/west` | legacy grid moves |

```json
{ "sessionId": "abc-123", "action": "interact" }
```

### `game_sync`

FPS-ввод на `deltaMs` (поворот к цели BFS, точное наведение):

```json
{ "sessionId": "abc-123", "yawDelta": 0.5, "deltaMs": 100 }
```

### `game_session_summary`

Компактный JSON: `phase`, `hp`, `keysCollected/Required`, `playerCell`, `exitGate`, список ключей.

### `game_list_actions`

Без аргументов — список допустимых строк для `game_act`.

## Пример stdio-сессии

```bash
# Запуск MCP
./gradlew :mcp-server:run --args=stdio

# tools/list
{"jsonrpc":"2.0","id":1,"method":"tools/list"}

# Новая игра
{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"game_new_session","arguments":{"seed":42}}}

# Наблюдение
{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"game_observe","arguments":{"sessionId":"SESSION_ID"}}}
```

## Agent loop

1. `game_new_session(seed)` → `sessionId`
2. Цикл до `LEVEL_COMPLETE` / `GAME_OVER` / budget:
   - `game_observe`
   - LLM (`LLM_PROVIDER=yandex`) или heuristic выбирает tool
   - выполнить tool
3. Победа: все ключи + `interact` на `EXIT_GATE`

## LLM providers

| `LLM_PROVIDER` | Описание |
|----------------|----------|
| `heuristic` | BFS KeyHuntPlanner, без API-ключей (default) |
| `yandex` | YandexGPT + fallback на heuristic |

## Ошибки валидации

Неверные аргументы возвращают `isError: true` с текстом, например:

```
Invalid action 'fly'. Use game_list_actions for allowed values.
```

Машиночитаемый контракт: [mcp-contract.json](./mcp-contract.json).
