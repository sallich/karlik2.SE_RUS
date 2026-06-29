# MCP Contract — Roguelike Agent

Оба агента (`agent-runner`, `policy-agent-runner`) взаимодействуют с игрой **только** через MCP-сервер. Прямой HTTP к `game-service` запрещён.

## Transport

| Режим | Когда |
|-------|--------|
| **stdio** (primary) | Docker и локальный запуск: `./gradlew :mcp-server:run --args=stdio` |
| **HTTP bridge** | Тесты и отладка: `GET /mcp/tools`, `POST /mcp/tools/call` |

JSON-RPC 2.0, по одному сообщению на строку (newline-delimited).

## Tools (6)

### `game_new_session`

Создаёт сессию.

```json
{ "seed": 42, "twoLevel": false, "coopAgent": false }
```

Ответ — полный `GameSnapshot` с `sessionId`.

### `game_observe`

```json
{ "sessionId": "abc-123" }
```

Снимок: карта, позиция, ключи, мобы, `phase`, инвентарь (агрегированно).

### `game_act`

Дискретное действие на сетке / в FPS-режиме:

| action | Эффект |
|--------|--------|
| `move_forward` | шаг вперёд по взгляду |
| `turn_left` / `turn_right` | поворот |
| `interact` | дверь / ключ / выход (E) |
| `wait` | пропуск шага |
| `move_north/south/east/west` | grid-ход (legacy) |

```json
{ "sessionId": "abc-123", "action": "interact" }
```

Опционально `actor`: `"player"` (default) или `"agent"` (кооп).

### `game_sync`

FPS-ввод на `deltaMs`: прицел, стрельба, reload, движение, прыжок.

```json
{
  "sessionId": "abc-123",
  "clientYaw": 1.57,
  "clientPitch": 0.0,
  "forward": true,
  "attack": true,
  "deltaMs": 100
}
```

Для калибровки yaw: `yawDelta`, `deltaMs` (используется step-agent).

### `game_session_summary`

Компактный JSON: `phase`, `hp`, `keysCollected/Required`, позиция, выход, ключи.

### `game_list_actions`

Без аргументов — список допустимых строк для `game_act`.

## Пример stdio-сессии

```bash
./gradlew :mcp-server:run --args=stdio

{"jsonrpc":"2.0","id":1,"method":"tools/list"}
{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"game_new_session","arguments":{"seed":42}}}
{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"game_observe","arguments":{"sessionId":"SESSION_ID"}}}
```

## Agent loop (общий контракт)

1. `game_new_session(seed)` → `sessionId`
2. Цикл до `LEVEL_COMPLETE` / `GAME_OVER` / budget:
   - `game_observe`
   - выбор действия (LLM или эвристика)
   - `game_act` или `game_sync`
3. Победа: собрать ключи + `interact` на выходе

**step-agent** (`agent-runner`): LLM выбирает tool call на каждом шаге (или `KeyHuntPlanner` при `LLM_PROVIDER=heuristic`).

**policy-agent** (`policy-agent-runner`): macro-LLM генерирует JSON-политику; micro-интерпретатор вызывает те же tools без LLM на каждом шаге.

## LLM providers (agent-runner)

| `LLM_PROVIDER` | Описание |
|----------------|----------|
| `heuristic` | `KeyHuntPlanner`, без API (default) |
| `ollama` | OpenAI-compatible API Ollama + retry → heuristic fallback |
| `yandex` | YandexGPT native API |
| `yandex-openai` | YandexGPT OpenAI-compatible |

Policy-agent: `POLICY_LLM_PROVIDER=ollama|heuristic` (отдельные env, см. [policy-agent.md](policy-agent.md)).

## Ошибки валидации

Неверные аргументы возвращают `isError: true`, например:

```
Invalid action 'fly'. Use game_list_actions for allowed values.
```

Машиночитаемый контракт: [mcp-contract.json](./mcp-contract.json).
