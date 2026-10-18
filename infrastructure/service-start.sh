#!/usr/bin/env bash
set -eo pipefail

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

set -a
. ./.env

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

if [ "$MODE" = "test" ]; then
    PROJECT="backoffice-gateway-test"
    PROFILE_ARGS=(--profile test)
    export SERVICE_PREFIX="$PROJECT"
    export PUBLISHED_API_PORT="${API_PORT:-8090}"

    ./service-stop.sh test
else
    PROJECT="backoffice-gateway"
    PROFILE_ARGS=()
    export SERVICE_PREFIX="$PROJECT"
    export PUBLISHED_API_PORT="${DEVELOPMENT_API_PORT:-8190}"

    ./service-stop.sh development
fi

docker compose -p "$PROJECT" "${PROFILE_ARGS[@]}" up -d --build --wait

echo "backoffice-gateway is up in $MODE mode at http://localhost:${PUBLISHED_API_PORT}"
