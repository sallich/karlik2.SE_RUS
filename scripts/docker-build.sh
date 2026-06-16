#!/usr/bin/env bash
# Сборка образов без параллельного Gradle (важно для WSL с малым RAM).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

export DOCKER_BUILDKIT=1
export COMPOSE_DOCKER_CLI_BUILD=1
export COMPOSE_PARALLEL_LIMIT="${DOCKER_BUILD_PARALLEL:-1}"

echo "==> docker compose build (COMPOSE_PARALLEL_LIMIT=$COMPOSE_PARALLEL_LIMIT) $*"
docker compose build "$@"
