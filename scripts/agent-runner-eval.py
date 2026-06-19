import json
import os
import subprocess
import sys
import time
from dataclasses import dataclass
from typing import List, Optional

import requests

PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEFAULT_COMPOSE_FILE = os.path.join(PROJECT_ROOT, "docker-compose.yml")

PROVIDERS = ["yandex", "yandex-openai"]      # Агенты для сравнения
SEEDS = [41, 42, 43, 44, 45]                 # Сиды (5 штук)
RUNS_PER_SEED = 5                            # Прогонов на сид
MAX_STEPS = 300                              # Бюджет шагов
AGENT_RUNNER_URL = "http://localhost:8082/api/v1/agent/run"
COMPOSE_SERVICE = "agent-runner"             # Имя сервиса в docker-compose
COMPOSE_FILE = DEFAULT_COMPOSE_FILE          # Путь к compose-файлу
LLM_API_KEY = ""
YANDEX_FOLDER_ID = ""

@dataclass
class RunResult:
    provider: str
    seed: int
    run_index: int
    steps_used: int
    steps_planned: int
    final_phase: str
    status: str
    success: bool
    tokens_used: Optional[int] = None
    message: str = ""
    error: Optional[str] = None

def run_agent(provider: str, seed: int, run_idx: int) -> RunResult:
    """Отправляет запрос к agent-runner и возвращает результат."""
    payload = {"seed": seed, "maxSteps": MAX_STEPS}
    try:
        resp = requests.post(AGENT_RUNNER_URL, json=payload, timeout=120)
        resp.raise_for_status()
        data = resp.json()
        final_phase = data.get("finalPhase", "UNKNOWN")
        return RunResult(
            provider=provider,
            seed=seed,
            run_index=run_idx,
            steps_used=data.get("stepsUsed", 0),
            steps_planned=data.get("stepsPlanned", 0),
            final_phase=final_phase,
            status=data.get("status", "UNKNOWN"),
            success=(final_phase == "LEVEL_COMPLETE"),
            tokens_used=data.get("tokensUsed"),
            message=data.get("message", ""),
        )
    except Exception as e:
        return RunResult(
            provider=provider,
            seed=seed,
            run_index=run_idx,
            steps_used=0,
            steps_planned=0,
            final_phase="ERROR",
            status="ERROR",
            success=False,
            error=str(e),
        )

def restart_agent(provider: str):
    """Перезапускает контейнер agent-runner с новым LLM_PROVIDER."""
    os.environ["LLM_PROVIDER"] = provider
    os.environ["LLM_API_KEY"] = LLM_API_KEY if LLM_API_KEY else ""
    os.environ["YANDEX_FOLDER_ID"] = YANDEX_FOLDER_ID if YANDEX_FOLDER_ID else ""

    subprocess.run(
        ["docker", "compose", "-f", COMPOSE_FILE, "stop", COMPOSE_SERVICE],
        check=False,
    )
    subprocess.run(
        ["docker", "compose", "-f", COMPOSE_FILE, "rm", "-f", COMPOSE_SERVICE],
        check=False,
    )

    subprocess.run(
        ["docker", "compose", "-f", COMPOSE_FILE, "up", "-d", COMPOSE_SERVICE],
        check=True,
    )

    health_url = AGENT_RUNNER_URL.replace("/api/v1/agent/run", "/health")
    wait_for_service(health_url)

def wait_for_service(url: str, max_retries: int = 30, delay: float = 2.0):
    """Ждёт, пока эндпоинт /health вернёт 200 OK."""
    for attempt in range(max_retries):
        try:
            resp = requests.get(url, timeout=5)
            if resp.status_code == 200:
                print(f"  Сервис готов (попытка {attempt+1})")
                return
        except requests.RequestException:
            pass
        time.sleep(delay)
    raise RuntimeError(f"Сервис {url} не поднялся за {max_retries * delay} сек.")


def main():
    print("=== Eval-скрипт для Roguelike ===")
    print(f"Провайдеры: {PROVIDERS}")
    print(f"Сиды: {SEEDS}")
    print(f"Прогонов на сид: {RUNS_PER_SEED}")
    print(f"Compose-файл: {COMPOSE_FILE}")

    all_results: List[RunResult] = []

    for provider in PROVIDERS:
        print(f"\n--- Провайдер: {provider} ---")
        restart_agent(provider)

        for seed in SEEDS:
            print(f"  Сид: {seed}")
            for run_idx in range(1, RUNS_PER_SEED + 1):
                print(f"    Прогон {run_idx}/{RUNS_PER_SEED} ...", end=" ", flush=True)
                result = run_agent(provider, seed, run_idx)
                all_results.append(result)
                status = "✅" if result.final_phase == "LEVEL_COMPLETE" else "❌"
                print(f"{status}  шагов={result.steps_used}, фаза={result.final_phase}, статус={result.status}")
                time.sleep(1)


    print("\n=== Сводка по провайдерам ===")
    summary = {}
    for provider in PROVIDERS:
        runs = [r for r in all_results if r.provider == provider]
        total = len(runs)
        successes = sum(1 for r in runs if r.success)
        success_rate = successes / total * 100 if total else 0
        avg_steps = sum(r.steps_used for r in runs if r.success) / successes if successes else 0
        avg_tokens = sum(r.tokens_used or 0 for r in runs if r.success) / successes if successes else 0

        summary[provider] = {
            "total": total,
            "successes": successes,
            "success_rate": success_rate,
            "avg_steps": avg_steps,
            "avg_tokens": avg_tokens,
        }


    print(f"{'Провайдер':<12} {'Успех':>8} {'Шаги (ср.)':>12} {'Токены (ср.)':>14}")
    for provider, stats in summary.items():
        print(f"{provider:<12} {stats['successes']:>4}/{stats['total']:>2} ({stats['success_rate']:>5.1f}%) "
              f"{stats['avg_steps']:>12.1f} {stats['avg_tokens']:>14.1f}")

    csv_file = os.path.join(PROJECT_ROOT, "eval_results.csv")
    with open(csv_file, "w", encoding="utf-8") as f:
        f.write("provider,seed,run_index,success,steps_used,final_phase,tokens_used,error\n")
        for r in all_results:
            f.write(f"{r.provider},{r.seed},{r.run_index},{r.success},{r.steps_used},"
                    f"{r.final_phase},{r.tokens_used or ''},{r.error or ''}\n")
    print(f"\nПодробные результаты сохранены в {csv_file}")

if __name__ == "__main__":
    main()