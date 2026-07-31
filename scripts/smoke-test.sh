#!/usr/bin/env bash
# =============================================================================
# TICKET-ADV153 — docker-compose smoke-test script
#
# WHAT:    Brings up the full docker-compose.yml stack, waits for every
#          service with a healthcheck to report healthy, then hits one
#          real HTTP endpoint per user-facing service (backend, prometheus,
#          grafana) to confirm they're actually serving traffic — not just
#          that the container process is alive.
# HOW:     `docker compose up -d` + `docker compose ps --format json` polled
#          in a loop, since `depends_on: condition: service_healthy` only
#          gates container start order, not this script's own exit status.
#          Does not touch docker-compose.yml itself — everything here reads
#          the existing 7-service stack (TICKET-ADV148/ADV152) as-is.
# WHY:     A green CI build proves the JAR compiles; it says nothing about
#          whether the containers actually boot together (wrong env var
#          name, a healthcheck that never turns green, a port collision).
#          This is the cheapest way to catch that class of bug before it
#          reaches a teammate's machine.
# USAGE:   ./scripts/smoke-test.sh          # up, verify, tear down
#          KEEP_UP=1 ./scripts/smoke-test.sh  # up, verify, leave running
# =============================================================================
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."

COMPOSE="docker compose"
TIMEOUT_SECONDS=180
POLL_INTERVAL=5

# Services declared with a `healthcheck:` in docker-compose.yml.
HEALTHCHECKED_SERVICES=(postgres kafka backend)

cleanup() {
    if [[ "${KEEP_UP:-0}" != "1" ]]; then
        echo "==> Tearing down stack"
        $COMPOSE down --volumes
    else
        echo "==> KEEP_UP=1 set, leaving the stack running"
    fi
}
trap cleanup EXIT

echo "==> Starting stack"
$COMPOSE up -d --build

echo "==> Waiting for healthchecked services: ${HEALTHCHECKED_SERVICES[*]}"
elapsed=0
while true; do
    all_healthy=true
    for service in "${HEALTHCHECKED_SERVICES[@]}"; do
        status=$($COMPOSE ps --format json "$service" | grep -o '"Health":"[a-z]*"' | cut -d'"' -f4 || true)
        if [[ "$status" != "healthy" ]]; then
            all_healthy=false
        fi
    done

    if $all_healthy; then
        echo "==> All healthchecked services are healthy (${elapsed}s)"
        break
    fi

    if (( elapsed >= TIMEOUT_SECONDS )); then
        echo "!! Timed out after ${TIMEOUT_SECONDS}s waiting for services to become healthy" >&2
        $COMPOSE ps
        exit 1
    fi

    sleep "$POLL_INTERVAL"
    elapsed=$((elapsed + POLL_INTERVAL))
done

check_endpoint() {
    local name=$1 url=$2
    if curl --fail --silent --show-error --max-time 5 "$url" > /dev/null; then
        echo "==> OK   $name ($url)"
    else
        echo "!! FAIL  $name ($url)" >&2
        exit 1
    fi
}

echo "==> Verifying endpoints"
check_endpoint "backend health"    "http://localhost:8080/api/actuator/health"
check_endpoint "backend metrics"   "http://localhost:8080/api/actuator/prometheus"
check_endpoint "prometheus ready"  "http://localhost:9090/-/healthy"
check_endpoint "grafana health"    "http://localhost:3000/api/health"

echo "==> Smoke test passed"
