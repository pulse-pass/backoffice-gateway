#!/usr/bin/env bash
# Usage: ./service-start.sh [test|development]
# test:        external dependencies are served by mocks (MockServer).
# development: external dependencies point to their sandbox environments.
set -e

MODE="${1:-test}"
if [ "$MODE" != "test" ] && [ "$MODE" != "development" ]; then
    echo "Invalid mode '$MODE'. Usage: ./service-start.sh [test|development]" >&2
    exit 1
fi

cd "$(dirname "$0")"

docker network inspect pulse-pass >/dev/null 2>&1 || docker network create pulse-pass

if [ ! -f .env ]; then
    cp .env.example .env
fi

./service-stop.sh

set -a
# shellcheck disable=SC1091
. ./.env

# For every <NAME>_TEST_URL / <NAME>_DEVELOPMENT_URL pair, export <NAME>_URL for the chosen mode.
SUFFIX="_$(echo "$MODE" | tr '[:lower:]' '[:upper:]')_URL"
for var in $(grep -oE '^[A-Z0-9_]+(_TEST_URL|_DEVELOPMENT_URL)' .env | sort -u); do
    case "$var" in
        *"$SUFFIX")
            name="${var%"$SUFFIX"}"
            export "${name}_URL=${!var}"
            ;;
    esac
done
set +a

PROFILE_ARGS=()
if [ "$MODE" = "test" ]; then
    PROFILE_ARGS=(--profile test)
fi

docker compose "${PROFILE_ARGS[@]}" up -d --build --wait

echo "backoffice-gateway is up in $MODE mode at http://localhost:${API_PORT}"
