#!/usr/bin/env bash
# =============================================================================
# scripts/verify-adv149-152.sh
#
# LIVE RUNTIME ACCEPTANCE SCRIPT for four workshop tickets:
#   TICKET-ADV149 — Prometheus scrapes the backend via its compose service
#                   name (backend:8080), never via localhost.
#   TICKET-ADV150 — Grafana auto-provisions the reconx-prometheus datasource
#                   and the ReconX dashboard folder, and every panel binds
#                   to that datasource.
#   TICKET-ADV151 — Liquibase owns the schema on startup; Hibernate's
#                   ddl-auto is pinned to `validate` under the uat profile.
#   TICKET-ADV152 — docker-compose healthchecks gate dependent services and
#                   every service reports (healthy).
#
# WHAT:    Brings up the docker-compose.yml stack (or reuses one already
#          running, via SKIP_BUILD=1), then hits Prometheus, Grafana, the
#          backend container logs, and postgres directly to prove each
#          ticket's acceptance criteria hold against the real running
#          containers — not mocks, not unit tests.
# HOW:     curl + jq against the live HTTP APIs (falls back to grep/sed if
#          jq is missing), `docker inspect` for container health status,
#          `docker compose logs` / `docker exec` for the backend + postgres.
#          Does not modify docker-compose.yml or any other existing file —
#          everything here reads the stack (TICKET-ADV148/ADV149/ADV150/
#          ADV151/ADV152) as-is.
# WHY:     Config drift between docker-compose.yml, prometheus.yml, the
#          Grafana provisioning files, and application-uat.yml is invisible
#          to a compiling JAR or a green unit-test suite. This is the
#          cheapest way to catch it before a teammate hits it live.
#
# USAGE:   ./scripts/verify-adv149-152.sh
#          SKIP_BUILD=1 ./scripts/verify-adv149-152.sh    # `up -d`, no --build
#          GRAFANA_USER=admin GRAFANA_PASSWORD=admin ./scripts/verify-adv149-152.sh
#
# The stack is left running on exit (this is a verification tool, not a
# smoke test) — see scripts/smoke-test.sh for a script that tears down.
# Exits 0 only if every check below passed; exits 1 otherwise.
# =============================================================================
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."

# --- colour helpers -----------------------------------------------------
if [[ -t 1 ]]; then
    RED=$'\033[0;31m'; GREEN=$'\033[0;32m'; YELLOW=$'\033[0;33m'
    BLUE=$'\033[0;34m'; BOLD=$'\033[1m'; RESET=$'\033[0m'
else
    RED=''; GREEN=''; YELLOW=''; BLUE=''; BOLD=''; RESET=''
fi

# --- config / env overrides ---------------------------------------------
COMPOSE="docker compose"
GRAFANA_USER="${GRAFANA_USER:-admin}"
GRAFANA_PASSWORD="${GRAFANA_PASSWORD:-admin}"
PROM_URL="http://localhost:9090"
GRAFANA_URL="http://localhost:3000"
GRAFANA_AUTH=(-u "${GRAFANA_USER}:${GRAFANA_PASSWORD}")

# --- jq detection (fall back to grep/sed, say so once) -------------------
HAVE_JQ=1
if ! command -v jq >/dev/null 2>&1; then
    HAVE_JQ=0
    echo "${YELLOW}==> jq not found — falling back to grep/sed for JSON parsing${RESET}"
fi

# --- PASS/FAIL tracking ---------------------------------------------------
PASS_COUNT=0
FAIL_COUNT=0
SUMMARY_TICKETS=()
SUMMARY_STATUSES=()
SUMMARY_DESCS=()
CURRENT_TICKET=""

section() {
    CURRENT_TICKET="$1"
    echo
    echo "${BOLD}${BLUE}=== $1: $2 ===${RESET}"
}

record() {
    SUMMARY_TICKETS+=("$1")
    SUMMARY_STATUSES+=("$2")
    SUMMARY_DESCS+=("$3")
}

pass() {
    PASS_COUNT=$((PASS_COUNT + 1))
    record "$CURRENT_TICKET" "PASS" "$1"
    echo "  ${GREEN}[PASS]${RESET} $1"
}

fail() {
    FAIL_COUNT=$((FAIL_COUNT + 1))
    record "$CURRENT_TICKET" "FAIL" "$1"
    echo "  ${RED}[FAIL]${RESET} $1" >&2
}

# poll_until <max_wait_seconds> <check_function> — calls check_function in a
# backoff loop (2s, 4s, 6s, 8s, 8s, ...) until it returns 0 or the budget
# expires. The check_function is responsible for setting whatever global it
# needs on success.
poll_until() {
    local max_wait=$1
    local check_fn=$2
    local elapsed=0 interval=2
    while (( elapsed < max_wait )); do
        if "$check_fn"; then
            return 0
        fi
        sleep "$interval"
        elapsed=$((elapsed + interval))
        if (( interval < 8 )); then
            interval=$((interval + 2))
        fi
    done
    return 1
}

# =============================================================================
# 1. Bring up the stack
# =============================================================================
echo "${BOLD}==> Bringing up stack${RESET}"
if [[ "${SKIP_BUILD:-0}" == "1" ]]; then
    $COMPOSE up -d
else
    $COMPOSE up -d --build
fi

# =============================================================================
# 2. TICKET-ADV152 — healthchecks
# =============================================================================
section "TICKET-ADV152" "container healthchecks reach healthy within budget"

# service:budget_seconds
HEALTH_TARGETS=("postgres:10" "kafka:30" "backend:60")
POLL_INTERVAL=2

for entry in "${HEALTH_TARGETS[@]}"; do
    svc="${entry%%:*}"
    budget="${entry##*:}"
    container="reconx-${svc}"
    elapsed=0
    status="unknown"

    while (( elapsed <= budget )); do
        status=$(docker inspect --format='{{.State.Health.Status}}' "$container" 2>/dev/null || echo "unknown")
        if [[ "$status" == "healthy" ]]; then
            break
        fi
        sleep "$POLL_INTERVAL"
        elapsed=$((elapsed + POLL_INTERVAL))
    done

    if [[ "$status" == "healthy" ]]; then
        pass "$container became healthy in ${elapsed}s (budget ${budget}s)"
    else
        fail "$container did not become healthy within ${budget}s (last status: $status)"
        echo "    last healthcheck log for $container:" >&2
        docker inspect --format='{{json .State.Health.Log}}' "$container" 2>/dev/null >&2 \
            || echo "    (no health log available — container may not define a HEALTHCHECK)" >&2
    fi
done

section "TICKET-ADV152" "docker compose ps reports (healthy) for every service"

CORE_SERVICES=(postgres zookeeper kafka backend frontend prometheus grafana)

# The services gated behind `depends_on: service_healthy` only start their own
# check once their dependency is healthy, so give the tail of the chain
# (frontend) its own window before snapshotting `docker compose ps`.
STACK_BUDGET=90
stack_elapsed=0
while (( stack_elapsed < STACK_BUDGET )); do
    stack_healthy=true
    for svc in "${CORE_SERVICES[@]}"; do
        st=$(docker inspect --format='{{.State.Health.Status}}' "reconx-${svc}" 2>/dev/null || echo "unknown")
        [[ "$st" == "healthy" ]] || stack_healthy=false
    done
    $stack_healthy && break
    sleep 2
    stack_elapsed=$((stack_elapsed + 2))
done

PS_OUTPUT=$($COMPOSE ps 2>&1 || true)

for svc in "${CORE_SERVICES[@]}"; do
    line=$(echo "$PS_OUTPUT" | grep -E "reconx-${svc}[[:space:]]" || true)
    if [[ -n "$line" && "$line" == *"(healthy)"* ]]; then
        pass "$svc shows (healthy) in 'docker compose ps'"
    else
        fail "$svc does not show (healthy) in 'docker compose ps' (${line:-not found})"
    fi
done

DEBUG_PS_OUTPUT=$($COMPOSE --profile debug ps 2>&1 || true)
if echo "$DEBUG_PS_OUTPUT" | grep -q "reconx-kafdrop"; then
    kafdrop_line=$(echo "$DEBUG_PS_OUTPUT" | grep "reconx-kafdrop" || true)
    if [[ "$kafdrop_line" == *"(healthy)"* ]]; then
        pass "kafdrop shows (healthy) under 'docker compose --profile debug ps'"
    else
        fail "kafdrop is listed under --profile debug but does not show (healthy) (${kafdrop_line:-not found})"
    fi
else
    echo "  ${YELLOW}[SKIP]${RESET} kafdrop not listed under 'docker compose --profile debug ps' — debug profile not active, skipping"
fi

# =============================================================================
# 3. TICKET-ADV149 — Prometheus scrapes the backend correctly
# =============================================================================
section "TICKET-ADV149" "Prometheus scrapes reconx-backend via backend:8080 (not localhost)"

# Polls until the backend target has completed its first successful scrape.
# Reaching the API is not enough: for the first scrape_interval after boot the
# target is legitimately reported "unknown"/"down".
fetch_prom_targets() {
    PROM_TARGETS_JSON=$(curl -sf --max-time 5 "$PROM_URL/api/v1/targets") || return 1
    [[ -n "$PROM_TARGETS_JSON" ]] || return 1
    echo "$PROM_TARGETS_JSON" \
        | sed 's/},{/}\n{/g' \
        | grep '"job":"reconx-backend"' \
        | grep -q '"health":"up"'
}

if ! poll_until 60 fetch_prom_targets; then
    PROM_TARGETS_JSON=""
fi

if [[ -z "${PROM_TARGETS_JSON:-}" ]]; then
    fail "could not reach $PROM_URL/api/v1/targets within 60s"
else
    if (( HAVE_JQ )); then
        BACKEND_HEALTH=$(echo "$PROM_TARGETS_JSON" | jq -r '[.data.activeTargets[] | select(.labels.job=="reconx-backend")][0].health // "missing"' || true)
        BACKEND_SCRAPE_URL=$(echo "$PROM_TARGETS_JSON" | jq -r '[.data.activeTargets[] | select(.labels.job=="reconx-backend")][0].scrapeUrl // ""' || true)
    else
        BACKEND_BLOCK=$(echo "$PROM_TARGETS_JSON" | sed 's/},{/}\n{/g' | grep '"job":"reconx-backend"' | head -1 || true)
        BACKEND_HEALTH=$(echo "$BACKEND_BLOCK" | grep -o '"health":"[a-z]*"' | head -1 | cut -d'"' -f4 || true)
        BACKEND_SCRAPE_URL=$(echo "$BACKEND_BLOCK" | grep -o '"scrapeUrl":"[^"]*"' | head -1 | cut -d'"' -f4 || true)
    fi

    if [[ "${BACKEND_HEALTH:-}" == "up" ]]; then
        pass "reconx-backend target health is up"
    else
        fail "reconx-backend target health is '${BACKEND_HEALTH:-missing}', expected 'up'"
    fi

    if [[ "${BACKEND_SCRAPE_URL:-}" == *"backend:8080"* && "${BACKEND_SCRAPE_URL:-}" == *"/api/actuator/prometheus"* ]]; then
        pass "reconx-backend scrapeUrl is correct ($BACKEND_SCRAPE_URL)"
    else
        fail "reconx-backend scrapeUrl '${BACKEND_SCRAPE_URL:-missing}' does not contain both backend:8080 and /api/actuator/prometheus"
    fi

    if echo "$PROM_TARGETS_JSON" | grep -q '"scrapeUrl":"[^"]*localhost:8080'; then
        fail "found a Prometheus target scrapeUrl containing localhost:8080"
    else
        pass "no Prometheus target scrapeUrl contains localhost:8080"
    fi
fi

fetch_prom_up_query() {
    PROM_QUERY_JSON=$(curl -sf --max-time 5 -G "$PROM_URL/api/v1/query" --data-urlencode 'query=up{job="reconx-backend"}') || return 1
    [[ -n "$PROM_QUERY_JSON" ]] || return 1
    # Keep polling while the series is absent or still reporting 0.
    echo "$PROM_QUERY_JSON" | grep -q '"value":\[[^]]*,"1"\]'
}

if ! poll_until 60 fetch_prom_up_query; then
    PROM_QUERY_JSON=""
fi

if [[ -z "${PROM_QUERY_JSON:-}" ]]; then
    fail "could not query up{job=\"reconx-backend\"} within 60s"
else
    if (( HAVE_JQ )); then
        UP_VALUE=$(echo "$PROM_QUERY_JSON" | jq -r '.data.result[0].value[1] // "missing"' || true)
    else
        UP_VALUE=$(echo "$PROM_QUERY_JSON" | grep -o '"value":\[[^]]*\]' | head -1 | grep -o '"[0-9]*"' | tail -1 | tr -d '"' || true)
    fi

    if [[ "${UP_VALUE:-}" == "1" ]]; then
        pass "up{job=\"reconx-backend\"} == 1"
    else
        fail "up{job=\"reconx-backend\"} returned '${UP_VALUE:-missing}', expected 1"
    fi
fi

# =============================================================================
# 4. TICKET-ADV150 — Grafana provisioning
# =============================================================================
section "TICKET-ADV150" "Grafana datasource + ReconX dashboard provisioning"

fetch_grafana_datasources() {
    GRAFANA_DATASOURCES_JSON=$(curl -sf --max-time 5 "${GRAFANA_AUTH[@]}" "$GRAFANA_URL/api/datasources") || return 1
    [[ -n "$GRAFANA_DATASOURCES_JSON" ]]
}

if ! poll_until 30 fetch_grafana_datasources; then
    GRAFANA_DATASOURCES_JSON=""
fi

if [[ -z "${GRAFANA_DATASOURCES_JSON:-}" ]]; then
    fail "could not reach $GRAFANA_URL/api/datasources within 30s"
else
    if (( HAVE_JQ )); then
        DS_MATCH=$(echo "$GRAFANA_DATASOURCES_JSON" | jq -r '[.[] | select(.uid=="reconx-prometheus" and .type=="prometheus" and .isDefault==true)] | length' || true)
    else
        DS_BLOCK=$(echo "$GRAFANA_DATASOURCES_JSON" | sed 's/},{/}\n{/g' | grep '"uid":"reconx-prometheus"' || true)
        if [[ -n "$DS_BLOCK" ]] && echo "$DS_BLOCK" | grep -q '"type":"prometheus"' && echo "$DS_BLOCK" | grep -q '"isDefault":true'; then
            DS_MATCH=1
        else
            DS_MATCH=0
        fi
    fi

    if [[ "${DS_MATCH:-0}" -ge 1 ]]; then
        pass "datasource reconx-prometheus (type=prometheus, isDefault=true) is provisioned"
    else
        fail "datasource reconx-prometheus with type=prometheus and isDefault=true was not found"
    fi
fi

fetch_grafana_dashboards() {
    GRAFANA_DASHBOARDS_JSON=$(curl -sf --max-time 5 "${GRAFANA_AUTH[@]}" "$GRAFANA_URL/api/search?type=dash-db") || return 1
    [[ -n "$GRAFANA_DASHBOARDS_JSON" ]]
}

if ! poll_until 30 fetch_grafana_dashboards; then
    GRAFANA_DASHBOARDS_JSON=""
fi

DASHBOARD_UID=""
if [[ -z "${GRAFANA_DASHBOARDS_JSON:-}" ]]; then
    fail "could not reach $GRAFANA_URL/api/search?type=dash-db within 30s"
else
    if (( HAVE_JQ )); then
        DASHBOARD_UID=$(echo "$GRAFANA_DASHBOARDS_JSON" | jq -r '[.[] | select(.folderTitle=="ReconX")][0].uid // ""' || true)
    else
        DASHBOARD_UID=$(echo "$GRAFANA_DASHBOARDS_JSON" | sed 's/},{/}\n{/g' | grep '"folderTitle":"ReconX"' | head -1 | grep -o '"uid":"[^"]*"' | head -1 | cut -d'"' -f4 || true)
    fi

    if [[ -n "$DASHBOARD_UID" ]]; then
        pass "found a dashboard in the ReconX folder (uid=$DASHBOARD_UID)"
    else
        fail "no dashboard found with folderTitle=ReconX"
    fi
fi

if [[ -n "$DASHBOARD_UID" ]]; then
    fetch_grafana_dashboard_detail() {
        GRAFANA_DASHBOARD_JSON=$(curl -sf --max-time 5 "${GRAFANA_AUTH[@]}" "$GRAFANA_URL/api/dashboards/uid/$DASHBOARD_UID") || return 1
        [[ -n "$GRAFANA_DASHBOARD_JSON" ]]
    }

    if ! poll_until 30 fetch_grafana_dashboard_detail; then
        GRAFANA_DASHBOARD_JSON=""
    fi

    if [[ -z "${GRAFANA_DASHBOARD_JSON:-}" ]]; then
        fail "could not fetch dashboard detail for uid=$DASHBOARD_UID"
    else
        # "row" panels are collapsible section headers — they carry no
        # datasource by design, so they are not part of this check.
        if (( HAVE_JQ )); then
            BAD_PANELS=$(echo "$GRAFANA_DASHBOARD_JSON" | jq -r '[.dashboard.panels[]? | select(.type != "row") | select(.datasource.uid != "reconx-prometheus")] | length' || true)
        else
            TOTAL_DS=$(echo "$GRAFANA_DASHBOARD_JSON" | grep -o '"datasource":{"type":"prometheus","uid":"[^"]*"}' | wc -l | tr -d ' ' || true)
            MATCHING_DS=$(echo "$GRAFANA_DASHBOARD_JSON" | grep -o '"datasource":{"type":"prometheus","uid":"reconx-prometheus"}' | wc -l | tr -d ' ' || true)
            BAD_PANELS=$(( ${TOTAL_DS:-0} - ${MATCHING_DS:-0} ))
        fi

        if [[ "${BAD_PANELS:-0}" -eq 0 ]]; then
            pass "every panel's datasource.uid is reconx-prometheus"
        else
            fail "$BAD_PANELS panel(s) do not use datasource.uid=reconx-prometheus"
        fi
    fi
fi

fetch_grafana_proxy_query() {
    GRAFANA_PROXY_JSON=$(curl -sf --max-time 5 "${GRAFANA_AUTH[@]}" -G "$GRAFANA_URL/api/datasources/proxy/uid/reconx-prometheus/api/v1/query" --data-urlencode "query=up") || return 1
    [[ -n "$GRAFANA_PROXY_JSON" ]]
}

if ! poll_until 60 fetch_grafana_proxy_query; then
    GRAFANA_PROXY_JSON=""
fi

if [[ -z "${GRAFANA_PROXY_JSON:-}" ]]; then
    fail "could not query Grafana's Prometheus proxy (up) within 60s"
else
    if (( HAVE_JQ )); then
        RESULT_COUNT=$(echo "$GRAFANA_PROXY_JSON" | jq -r '.data.result | length' || true)
    else
        RESULT_COUNT=$(echo "$GRAFANA_PROXY_JSON" | grep -o '"metric":{' | wc -l | tr -d ' ' || true)
    fi

    if [[ "${RESULT_COUNT:-0}" -gt 0 ]]; then
        pass "Grafana's Prometheus proxy query 'up' returned $RESULT_COUNT result(s) — panels have data"
    else
        fail "Grafana's Prometheus proxy query 'up' returned no results"
    fi
fi

# =============================================================================
# 5. TICKET-ADV151 — Liquibase owns the schema; ddl-auto is validate
# =============================================================================
section "TICKET-ADV151" "Liquibase runs before Tomcat; ddl-auto is validate"

BACKEND_LOGS=$($COMPOSE logs backend 2>&1 || true)

LIQUIBASE_LINE=$(echo "$BACKEND_LOGS" | grep -in "liquibase" | head -1 | cut -d: -f1 || true)
# "Started ReconxApplication in 6.2 seconds" — anchored on " in " so the
# earlier "Starting ReconxApplication v1.0.0" banner does not match.
STARTED_LINE=$(echo "$BACKEND_LOGS" | grep -inE "Started [A-Za-z]*Application in " | head -1 | cut -d: -f1 || true)

if [[ -z "$LIQUIBASE_LINE" ]]; then
    fail "no Liquibase log line found in 'docker compose logs backend'"
elif [[ -z "$STARTED_LINE" ]]; then
    fail "no 'Started ...Application' log line found in 'docker compose logs backend'"
elif (( LIQUIBASE_LINE < STARTED_LINE )); then
    pass "Liquibase logs (line $LIQUIBASE_LINE) run before Started ReconxApplication (line $STARTED_LINE)"
else
    fail "Liquibase logs (line $LIQUIBASE_LINE) do NOT run before Started ReconxApplication (line $STARTED_LINE)"
fi

DBCHANGELOG_COUNT=$(docker exec reconx-postgres psql -U reconx_user -d reconx -tAc "SELECT count(*) FROM databasechangelog;" 2>/dev/null | tr -d '[:space:]' || echo "")

if [[ "${DBCHANGELOG_COUNT:-}" =~ ^[0-9]+$ ]] && (( DBCHANGELOG_COUNT > 0 )); then
    pass "databasechangelog has $DBCHANGELOG_COUNT row(s)"
else
    fail "databasechangelog count query failed or returned 0 (got '${DBCHANGELOG_COUNT:-empty}')"
fi

PROFILE_COUNT=$(docker exec reconx-backend env 2>/dev/null | grep -c '^SPRING_PROFILES_ACTIVE=uat$' || true)

if [[ "${PROFILE_COUNT:-0}" -ge 1 ]]; then
    pass "reconx-backend container has SPRING_PROFILES_ACTIVE=uat"
else
    fail "reconx-backend container does not have SPRING_PROFILES_ACTIVE=uat"
fi

if grep -qE '^[[:space:]]*ddl-auto:[[:space:]]*validate[[:space:]]*$' backend/src/main/resources/application-uat.yml; then
    pass "application-uat.yml pins hibernate ddl-auto to validate"
else
    fail "application-uat.yml does not pin ddl-auto to validate"
fi

# =============================================================================
# Summary
# =============================================================================
echo
echo "${BOLD}${BLUE}=== Summary ===${RESET}"
printf '%-16s %-6s %s\n' "TICKET" "STATUS" "CHECK"
printf '%-16s %-6s %s\n' "------" "------" "-----"

i=0
total=${#SUMMARY_TICKETS[@]}
while (( i < total )); do
    t="${SUMMARY_TICKETS[$i]}"
    s="${SUMMARY_STATUSES[$i]}"
    d="${SUMMARY_DESCS[$i]}"
    if [[ "$s" == "PASS" ]]; then
        color="$GREEN"
    else
        color="$RED"
    fi
    printf '%-16s %s%-6s%s %s\n' "$t" "$color" "$s" "$RESET" "$d"
    i=$((i + 1))
done

echo
echo "${BOLD}Total: $((PASS_COUNT + FAIL_COUNT))   ${GREEN}PASS: ${PASS_COUNT}${RESET}${BOLD}   ${RED}FAIL: ${FAIL_COUNT}${RESET}"

if (( FAIL_COUNT > 0 )); then
    echo
    echo "${RED}${BOLD}verify-adv149-152.sh: FAILED (${FAIL_COUNT} check(s) failed)${RESET}"
    exit 1
fi

echo
echo "${GREEN}${BOLD}verify-adv149-152.sh: ALL CHECKS PASSED${RESET}"
