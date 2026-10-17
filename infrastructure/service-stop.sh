#!/usr/bin/env bash
# Usage: ./service-stop.sh
set -e

cd "$(dirname "$0")"

docker compose --profile test down -v --remove-orphans --rmi local
