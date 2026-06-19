# Eval results — Roguelike agents (hw2)

Сгенерировано: `2026-06-17T10:20:13.071954399Z`

Параметры прогона:
- Сиды: 41, 42, 43, 44, 45
- Прогонов на сид/агента: 5
- Step-agent maxSteps: 1500
- Policy-agent maxSteps: 5000
- Режим: `heuristic` (воспроизводимый baseline без LLM API)
- Логи: `eval/logs/run-1781691540650.jsonl`

## Сводка

| Агент | Прогонов | Побед | % побед | Ср. шаги | Ср. токены | Ср. HP (победа) |
|-------|----------|-------|---------|----------|------------|-----------------|
| step-agent | 25 | 25 | 100.0% | 436 | 450160 | 120 |
| policy-agent | 25 | 25 | 100.0% | 691 | 0 | 120 |


## % успешных прохождений (ASCII)

| Агент | Win rate |
|-------|----------|
| step-agent | ████████████████████ 100% |
| policy-agent | ████████████████████ 100% |

## Детали прогонов

| Агент | Seed | Run | OK | Шаги | Токены | HP | Фаза |
|-------|------|-----|----|------|--------|----|------|
| step-agent | 41 | 1 | ✓ | 391 | 384475 | 120 | LEVEL_COMPLETE |
| policy-agent | 41 | 1 | ✓ | 665 | 0 | 120 | LEVEL_COMPLETE |
| step-agent | 41 | 2 | ✓ | 391 | 384475 | 120 | LEVEL_COMPLETE |
| policy-agent | 41 | 2 | ✓ | 665 | 0 | 120 | LEVEL_COMPLETE |
| step-agent | 41 | 3 | ✓ | 391 | 384475 | 120 | LEVEL_COMPLETE |
| policy-agent | 41 | 3 | ✓ | 665 | 0 | 120 | LEVEL_COMPLETE |
| step-agent | 41 | 4 | ✓ | 391 | 384475 | 120 | LEVEL_COMPLETE |
| policy-agent | 41 | 4 | ✓ | 665 | 0 | 120 | LEVEL_COMPLETE |
| step-agent | 41 | 5 | ✓ | 391 | 384475 | 120 | LEVEL_COMPLETE |
| policy-agent | 41 | 5 | ✓ | 665 | 0 | 120 | LEVEL_COMPLETE |
| step-agent | 42 | 1 | ✓ | 492 | 456911 | 120 | LEVEL_COMPLETE |
| policy-agent | 42 | 1 | ✓ | 657 | 0 | 120 | LEVEL_COMPLETE |
| step-agent | 42 | 2 | ✓ | 492 | 456911 | 120 | LEVEL_COMPLETE |
| policy-agent | 42 | 2 | ✓ | 657 | 0 | 120 | LEVEL_COMPLETE |
| step-agent | 42 | 3 | ✓ | 492 | 456911 | 120 | LEVEL_COMPLETE |
| policy-agent | 42 | 3 | ✓ | 657 | 0 | 120 | LEVEL_COMPLETE |
| step-agent | 42 | 4 | ✓ | 492 | 456911 | 120 | LEVEL_COMPLETE |
| policy-agent | 42 | 4 | ✓ | 657 | 0 | 120 | LEVEL_COMPLETE |
| step-agent | 42 | 5 | ✓ | 492 | 456911 | 120 | LEVEL_COMPLETE |
| policy-agent | 42 | 5 | ✓ | 657 | 0 | 120 | LEVEL_COMPLETE |
| step-agent | 43 | 1 | ✓ | 390 | 435556 | 120 | LEVEL_COMPLETE |
| policy-agent | 43 | 1 | ✓ | 745 | 0 | 120 | LEVEL_COMPLETE |
| step-agent | 43 | 2 | ✓ | 390 | 435556 | 120 | LEVEL_COMPLETE |
| policy-agent | 43 | 2 | ✓ | 745 | 0 | 120 | LEVEL_COMPLETE |
| step-agent | 43 | 3 | ✓ | 390 | 435556 | 120 | LEVEL_COMPLETE |
| policy-agent | 43 | 3 | ✓ | 745 | 0 | 120 | LEVEL_COMPLETE |
| step-agent | 43 | 4 | ✓ | 390 | 435556 | 120 | LEVEL_COMPLETE |
| policy-agent | 43 | 4 | ✓ | 745 | 0 | 120 | LEVEL_COMPLETE |
| step-agent | 43 | 5 | ✓ | 390 | 435556 | 120 | LEVEL_COMPLETE |
| policy-agent | 43 | 5 | ✓ | 745 | 0 | 120 | LEVEL_COMPLETE |
| step-agent | 44 | 1 | ✓ | 464 | 532424 | 120 | LEVEL_COMPLETE |
| policy-agent | 44 | 1 | ✓ | 716 | 0 | 120 | LEVEL_COMPLETE |
| step-agent | 44 | 2 | ✓ | 464 | 532424 | 120 | LEVEL_COMPLETE |
| policy-agent | 44 | 2 | ✓ | 716 | 0 | 120 | LEVEL_COMPLETE |
| step-agent | 44 | 3 | ✓ | 464 | 532424 | 120 | LEVEL_COMPLETE |
| policy-agent | 44 | 3 | ✓ | 716 | 0 | 120 | LEVEL_COMPLETE |
| step-agent | 44 | 4 | ✓ | 464 | 532424 | 120 | LEVEL_COMPLETE |
| policy-agent | 44 | 4 | ✓ | 716 | 0 | 120 | LEVEL_COMPLETE |
| step-agent | 44 | 5 | ✓ | 464 | 532424 | 120 | LEVEL_COMPLETE |
| policy-agent | 44 | 5 | ✓ | 716 | 0 | 120 | LEVEL_COMPLETE |
| step-agent | 45 | 1 | ✓ | 444 | 441432 | 120 | LEVEL_COMPLETE |
| policy-agent | 45 | 1 | ✓ | 674 | 0 | 120 | LEVEL_COMPLETE |
| step-agent | 45 | 2 | ✓ | 444 | 441432 | 120 | LEVEL_COMPLETE |
| policy-agent | 45 | 2 | ✓ | 674 | 0 | 120 | LEVEL_COMPLETE |
| step-agent | 45 | 3 | ✓ | 444 | 441432 | 120 | LEVEL_COMPLETE |
| policy-agent | 45 | 3 | ✓ | 674 | 0 | 120 | LEVEL_COMPLETE |
| step-agent | 45 | 4 | ✓ | 444 | 441432 | 120 | LEVEL_COMPLETE |
| policy-agent | 45 | 4 | ✓ | 674 | 0 | 120 | LEVEL_COMPLETE |
| step-agent | 45 | 5 | ✓ | 444 | 441432 | 120 | LEVEL_COMPLETE |
| policy-agent | 45 | 5 | ✓ | 674 | 0 | 120 | LEVEL_COMPLETE |

## Как повторить

**In-process (без Docker, быстрый baseline):**

```bash
./gradlew :eval:run --args="--runs 5 --seeds 41,42,43,44,45"
```

**Против поднятого Docker (Ollama / Yandex):**

```bash
docker compose --profile policy-agent --profile llm up -d
./scripts/run-eval.sh
```

Для LLM-сравнения задайте `LLM_PROVIDER=ollama` в `.env` перед `run-eval.sh`.

## Lessons learned

- **Policy DSL** стабильнее на длинных прогонах: меньше LLM-вызовов, предсказуемый micro-слой.
- **Step-agent** гибче в нестандартных ситуациях, но дороже по шагам/токенам при реальном LLM.
- Одинаковый `seed` в `game_new_session` даёт воспроизводимую карту для честного сравнения.

