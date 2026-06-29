# Policy DSL Agent (`policy-agent-runner`)

Второй агент для сравнения в eval (hw2). Отличается от пошагового `agent-runner`:

| | **agent-runner** (step) | **policy-agent-runner** (DSL) |
|--|-------------------------|-------------------------------|
| LLM на каждом шаге | да | нет |
| LLM генерирует | один MCP tool call | **AgentPolicy** JSON v4 |
| Micro-уровень | LLM tool call или KeyHuntPlanner | **PolicyInterpreter** → planners |
| Порт | 8082 | 8083 |

## Как это работает

```
Старт run
  └─ LLM (sync): AgentPolicy JSON — objective + rules + params
       │
       ▼  каждый micro-шаг (без LLM):
  PolicyInterpreter
    1) guard-правила (код)     — бой, reload, door-E, near_key, room_exit, …
    2) objective (от LLM)      — enter_door / reach_key / explore / …
    3) fallback rules          — has_visible_key, needs_keys, explore
    4) planners (код)          — PolicyDoorPlanner, KeyHunt, Combat, …
       │
       ▼  параллельно, не блокируя шаги (кроме LOOP_ESCAPE):
  async replan → LLM patch / новый objective
```

| Кто решает | Что |
|------------|-----|
| **LLM (macro)** | Один `objective` (куда идти) + какие `rules` включены + `params` (exploreMode, unstuckMode). Не выбирает tool call на каждом шаге. |
| **Код (guards)** | Рефлексы с **фиксированным приоритетом**: `needs_reload`, `combat_in_room`, `can_interact`, `near_visible_key`, `needs_room_exit` **всегда** перебивают objective. LLM их не «выключает» в критический момент — они в baseline. |
| **Код (planners)** | Детерминированное исполнение: pathfinding, FPS-sync, interact, `PolicyCombatHelper` (kite-reload). |

**Replan** — не только «застряли». Триггеры (по приоритету): `ACTION_ERROR` → `LOOP_ESCAPE` (**sync**, micro ждёт) → `DOOR_STUCK` → `STUCK` → `NO_PROGRESS` → `PHASE_CHANGE` → `KEY_COLLECTED` → `OBJECTIVE_DONE` → `ROOM_TIMER_CHANGE` (комната зачищена) → `INTERVAL` (каждые `POLICY_REPLAN_EVERY_STEPS` шагов). Обычные replan **async** — агент продолжает ходить по старой политике, пока Ollama не ответит; если Ollama недоступна → `PolicyDeterministicPatch` (**минимальный**: сохраняет objective/params LLM, включает только reflex-правила).

**Контекст LLM:** каждый вызов stateless (`system` + один `user` brief). История шагов в чат **не** копится (в отличие от step-agent).

**Tracker debug:** `macro: ollama · ollama-initial-qwen2.5:3b` — откуда взялась политика; `policy: needs_reload` — какой guard сработал на этом шаге; `objective: explore target=…` — committed цель LLM.

## Архитектура v2 (4 слоя)

```
Observation → Macro (LLM policy) → Interpreter → Planners → MCP
     ↑              ↑ async replan queue + deterministic patch
Session directives (exit cleared mob room)
```

## DSL v4 — objective (LLM принимает решения, планировщики исполняют)

Главное решение принимает LLM: на старте и на каждом async-replan она коммитит **один**
`objective` (под-цель). Интерпретатор держит её весь окно replan'а — это убирает per-step
переключение цели, из-за которого агент «заходил-выходил» из коридора/двери. Правила (`rules`)
остаются только как реактивные guard'ы (бой/перезарядка/дверь-E/stuck) и fallback.

```json
{"version":4,
 "objective":{"kind":"enter_door|reach_key|reach_exit|explore|clear_room","target":"x,y","commitSteps":20},
 "params":{...},"rules":[...]}
```

- `target` — конкретная клетка, **скопированная из brief** (fair-play): `knownDoors`,
  `frontierTargets`, `knownExitGate`, видимый ключ. Глобальной карты у LLM нет.
- `commitSteps` — 5..60; сколько шагов держать цель прежде чем спросить LLM снова.
- Приоритет в интерпретаторе: **guard-правила → objective → остальные правила → explore**.
- `ReplanReason.OBJECTIVE_DONE` — когда цель достигнута / невалидна / истёк commit, async-replan
  просит у LLM следующую цель (это и есть «junction», где LLM реально играет, без per-step вызовов).
- `PolicyDoorPlanner` коммитит одну approach-клетку у двери (нет колебаний `dc-1`↔`dc+1`).
- Кэш макро-политик хранит политику **без** `objective` (его `target` — координаты конкретной карты).

### 1. Observation (`PolicyObservation`)
- `situation`: combat | leave_room | at_door_ready | at_door_blocked | corridor | …
- `failureSignals`: DOOR_E_NOT_READY, PING_PONG, SYNC_NO_PROGRESS, INVENTORY_FULL, …
- Попадает в replan brief для LLM

### 2. Macro — AgentPolicy (rules + objective)
Новые conditions: `at_door_ready`, `at_door_need_enter`, `inventory_full`, `needs_weapon`, `has_visible_item`

Новые actions: `approach_door`, `enter_door`, `navigate_item`, `equip_weapon`, `manage_inventory`

### 3. Micro — planners (policy-agent-runner)
| Action | Planner |
|--------|---------|
| approach_door / enter_door | `PolicyDoorPlanner` (FPS + E) |
| exit_room | `PolicyRoomExitPlanner` |
| navigate_* / explore | `PolicyNavigation` (FPS, без compass) |
| unstuck | `PolicyUnstuckPlanner` |
| equip / inventory | `PolicyItemPlanner` + `game_sync` hotbar fields |

**Session directive:** `session:needs_room_exit` — принудительный выход из зачищенной mob-комнаты (не из JSON LLM).

**Ложный room-exit (залы с колоннами/лифтом).** Pathfinding policy-агента наземный (`localHeight=0`), поэтому зал из `COLUMN` + `ELEVATOR` может ошибочно читаться как «комната с дверьми»: щели между колоннами выглядят выходами → ping-pong. Защита:
- `PolicyRoomExitPlanner.corridorExitNeighbors` — геометрический выход засчитывается только как «горловина коридора» (≤2 проходимых соседа), а не как открытый пол за колонной;
- `PolicyRoomExitPlanner.hasResolvableExit` — реальный выход = достижимая frozen-цель (захвачена при входе с запечатанной дверью) **или** `mobRoom`-маркер двери (без геометрии);
- `PolicyContext.releaseFalseRoomExitIfStuck` — если `seekRoomExit` залип (`ROOM_EXIT_STUCK`/`PING_PONG`) и реального выхода нет, состояние отпускается, и управление возвращается обычной навигации (objective/explore к frontier/лифту). **Жёсткий сброс:** при затяжном трэше (`roomExitStuckStreak ≥ ROOM_EXIT_HARD_RELEASE = 6`) состояние отпускается безусловно — даже если дверь номинально «достижима», т.к. постоянный ping-pong означает, что подход к выходу не работает (например, exit-цели резолвятся во внутренние клетки у двери). `needs_room_exit` — жёсткий guard, который сам LLM/objective перебить не может, поэтому без этого сброса агент мог зацикливаться навсегда.

**Вертикальный pathfinding (колонны/лифт).** `PolicyFpsPathfinder` получил opt-in флаг `allowVertical`: `COLUMN` становится проходимым «приподнятым» узлом — прыжок наверх (апекс прыжка ≈0.63 > `COLUMN_HEIGHT=0.45`), бег по верхам колонн и спрыгивание; коллизия шага, затрагивающего колонну, считается на высоте `COLUMN_HEIGHT`, где колонны не блокируют движение (`WorldVertical.blocksMovementAt`), а стены блокируют на любой высоте. Прыжок в навигации уже выдаётся `PolicyVerticalHelper.shouldJumpForMove`. `allowVertical=true` включён только в маршрутных вызовах (room-exit `fpsPath`/`fpsNearest`, explore, key-hunt, item, door, unstuck); захват региона комнаты и геометрия выходов остаются наземными, чтобы колонны не читались как часть комнаты или как двери.

**Тупик у двери (`AT_DOOR_BLOCKED` / `DOOR_E_NOT_READY`).** «Я у двери» (`isNearAnyDoorSeal`, по маркеру) и «можно нажать E» (`canPressE`, по тайлу `ROOM_SEAL` + маркеру на той же клетке) — разные критерии. У loot-двери (`WEAPON_*`) маркер может не совпадать с тайлом печати, тогда `canPressE` навсегда false. Защита:
- `PolicyDoorPlanner.interactableSealCell` — цель входа = реальная клетка `ROOM_SEAL`/`ROOM_DOOR` **с маркером** (а не сырой маркер); прицел/подход считаются от неё. Если рядом нет такой печати — дверь не входибельна, планировщик уходит в fallback (key-hunt/explore).
- `PolicyContext.updateDoorInteraction` + `deadDoorKeys` — phantom loot-door (нет pressable seal) помечается **мёртвой сразу**; иначе после 3 шагов `at door && !canPressE`. `atDoorNeedEnter`-гард перестаёт срабатывать (`isNearestDoorDead`).
- `PolicyKeyHuntPlanner` — **не** вызывает `interact`, если игрок уже на approach-клетке, но `canPressE=false` (раньше бил `interact` в стену). Вместо этого — `PolicyDoorPlanner` (поворот к seal) или explore к frontier.
- `fallback` при dead blocked door → explore к frontier (не зацикливание на той же двери).

### 4. Replan
- **Очередь** приоритетов: ACTION_ERROR → **LOOP_ESCAPE (sync)** → ROOM_TIMER → DOOR_STUCK → STUCK → …
- **Async** — micro не ждёт LLM (обычные репланы)
- **Sync loop escape** — если один и тот же guard/позиция повторяется (ping-pong, `at_door_need_enter` ↔ retreat), micro **блокируется** до ответа LLM с новым сценарием (как initial policy). В промпт уходит action trace + frontier/doors.
- **Deterministic patch** если Ollama недоступна (**только** `POLICY_LLM_PROVIDER=heuristic` или initial fail). В режиме `ollama` replan **никогда** не подменяет стратегию кодом — при fail LLM политика **сохраняется** (`llm-replan-failed-keep-current`).

**Replan по расписанию:** `INTERVAL` не срабатывает, пока активный `objective` валиден и не истёк `commitSteps` (задаёт LLM). Следующий macro-вызов — по `OBJECTIVE_DONE` или истечению commit.

**LOOP_ESCAPE:** не срабатывает на стабильном `objective:*` при движении (раньше 4–5 шагов `explore` ошибочно считались «петлёй» → sync replan → `deterministic-patch`). Реальная петля = ping-pong позиций или повтор guard'ов (`at_door_need_enter` ↔ `stuck`).

**Ping-pong у двери после 1-й комнаты.** Guard `at_door_need_enter` вызывал `PolicyDoorPlanner` **без** `PolicyContext` → каждый шаг заново выбиралась другая approach-клетка; параллельно `stuck`/`unstuck` откатывал на 2 клетки назад. Фикс: передача `context` во все вызовы door planner (commitment к одной approach-клетке); `stuck`/`corner_trapped` не срабатывают при door-oscillation; при накоплении loop → sync `LOOP_ESCAPE` replan.

## LLM как мозг (без per-step вызовов)

Per-step LLM (как step-agent) — медленно и нестабильно. LLM — **единственный источник стратегии**: objective, params, phase, patch rules. Код не навязывает archetype и не ротирует цели через `runNonce`.

| Рычаг | Пример | Эффект |
|-------|--------|--------|
| `objective` | `reach_key @ 4,37` | куда идти 5–60 шагов |
| `params.combatStyle` | `plant` / `kite` / `chase` | стиль боя каждый шаг |
| `params.keyPriority` | `keys_first` / `doors_first` | порядок ключи vs двери |
| `params.exploreMode`, `unstuckMode` | `frontier`, `retreat` | explore/unstuck planners |
| `params.riskLevel` | `cautious` / `aggressive` | порог COMBAT_STALEMATE replan |
| `phase` + `notes` | `door_hunter`, «Rush mob door» | видимая метка стратегии в tracker |
| `patch` rules | `combat_in_room→combat`, `explore disabled` | включить/выключить conditions |
| **Replan** | `COMBAT_STALEMATE`, `INTERVAL`, `STUCK` | LLM пересматривает стратегию |

**Жёсткие guards** (бой, reload, door-E, near_key) остаются — это «рефлексы». LLM не может отключить `needs_reload`, но может сменить `combatStyle` и правила fallback.

`COMBAT_STALEMATE` — если HP мобов не падает ~35 шагов в комнате, async-replan просит LLM сменить тактику. Replan brief — только факты (`replanContext`), без готовых рецептов.

**Разнообразие прогонов:** `runNonce` + `temperature` + `top_p` + `llmSampleSeed` в Ollama options. `StrategyArchetypes` — только примеры в system prompt, код их не выбирает.

Tracker: `runNonce=… llmSampleSeed=…`, `phase`, `llm: "…"`, `macroJournal`, `macroDecisions[]`. Источник решения: `macro: ollama · ollama-initial-…` vs `deterministic-patch-*` / `heuristic-fallback` (обход LLM).

## Запуск

```bash
docker compose --profile policy-agent up --build
# Ollama уже default; для baseline без LLM:
# POLICY_LLM_PROVIDER=heuristic docker compose --profile policy-agent up --build

curl -X POST http://localhost:8083/api/v1/policy-agent/run \
  -H "Content-Type: application/json" \
  -d '{"seed":41,"maxSteps":2000}'
```

### Переменные окружения (policy-agent)

| Переменная | Default (compose) | Назначение |
|------------|-------------------|------------|
| `POLICY_LLM_PROVIDER` | `ollama` | `ollama` / `heuristic` |
| `POLICY_OLLAMA_MODEL` | `qwen2.5:3b` | primary macro policy |
| `POLICY_LLM_REQUEST_TIMEOUT_MS` | `180000` | не падать на 1.5b слишком рано |
| `POLICY_REPLAN_EVERY_STEPS` | `25` | periodic async replan |
| `POLICY_STUCK_THRESHOLD` | `3` | шагов без движения → stuck |
| `POLICY_MACRO_CACHE` | `false` | кэш политик по situation (без objective) |
| `POLICY_LLM_INITIAL_TEMPERATURE` | `0.75` | разброс initial policy |
| `POLICY_LLM_REPLAN_TEMPERATURE` | `0.85` | разброс replan patch |
| `POLICY_LLM_TOP_P` | `0.92` | nucleus sampling (Ollama) |

Tracker: http://localhost:8083/api/v1/policy-agent/tracker — `macroJournal` (журнал решений LLM), `debugLines` с phase/notes/objective.
