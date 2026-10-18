#!/usr/bin/env bash
set -eo pipefail

MODE="${1:-test}"
if [ "$MODE" != "test" ] && [ "$MODE" != "development" ]; then
    echo "Invalid mode '$MODE'. Usage: ./service-stop.sh [test|development]" >&2
    exit 1
fi

cd "$(dirname "$0")"

if [ "$MODE" = "test" ]; then
    docker compose -p backoffice-gateway-test --profile test down -v --remove-orphans --rmi local
else
    docker compose -p backoffice-gateway down -v --remove-orphans --rmi local
fi
