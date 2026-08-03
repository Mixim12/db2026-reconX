#!/usr/bin/env bash
# =============================================================================
# TICKET-ADV146 / ADV147 / ADV148 — containerisation acceptance tests
#
# WHAT: One executable assertion per acceptance criterion of the three
#       Day-10 Part-A tickets:
#         ADV146 — backend multi-stage Dockerfile
#         ADV147 — frontend multi-stage Dockerfile + nginx.conf
#         ADV148 — docker-compose.yml with 7 services
# WHY:  These criteria are all about runtime behaviour of images and the
#       compose network — a green `mvn test` says nothing about them.
#       Everything here is checked against a real build / real containers.
# USAGE:
#       ./scripts/verify-adv146-148.sh            # all groups
#       ./scripts/verify-adv146-148.sh backend    # ADV146 only
#       ./scripts/verify-adv146-148.sh frontend   # ADV147 only
#       ./scripts/verify-adv146-148.sh compose    # ADV148 only (slow, boots stack)
# =============================================================================
set -uo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."

BACKEND_IMAGE="reconx-backend:test"
FRONTEND_IMAGE="reconx-frontend:test"
FRONTEND_PORT=8081
COMPOSE_TIMEOUT_SECONDS=240
COMPOSE_POLL_INTERVAL=5
EXPECTED_SERVICES=(postgres zookeeper kafka backend frontend prometheus grafana)

PASSED=0
FAILED=0

pass() { printf '  \033[32mPASS\033[0m  %s\n' "$1"; PASSED=$((PASSED + 1)); }
fail() { printf '  \033[31mFAIL\033[0m  %s\n' "$1"; FAILED=$((FAILED + 1)); }
group() { printf '\n\033[1m== %s\033[0m\n' "$1"; }

# assert_ok <description> <command...>
assert_ok() {
    local description="$1"; shift
    if "$@" >/dev/null 2>&1; then pass "$description"; else fail "$description"; fi
}

# image_size_mb <image> — final image size in whole megabytes
image_size_mb() {
    local bytes
    bytes=$(docker image inspect "$1" --format '{{.Size}}' 2>/dev/null) || return 1
    echo $((bytes / 1024 / 1024))
}

# -----------------------------------------------------------------------------
# TICKET-ADV146 — backend image
# -----------------------------------------------------------------------------
verify_backend() {
    group "TICKET-ADV146 — backend multi-stage Dockerfile"

    # The build context is the repo root: backend/pom.xml depends on the sibling
    # module recon-audit-starter, which is not reachable from a backend/ context.
    if docker build -q -f backend/Dockerfile -t "$BACKEND_IMAGE" . >/dev/null 2>&1; then
        pass "image builds from repo-root context (-f backend/Dockerfile .)"
    else
        fail "image builds from repo-root context (-f backend/Dockerfile .)"
        return
    fi

    local size
    size=$(image_size_mb "$BACKEND_IMAGE")
    if [[ -n "$size" && "$size" -lt 250 ]]; then
        pass "final image is ${size} MB (< 250 MB)"
    else
        fail "final image is ${size:-unknown} MB (expected < 250 MB)"
    fi

    # Runtime stage must not carry the JDK or Maven.
    if docker run --rm --entrypoint sh "$BACKEND_IMAGE" -c 'command -v javac' >/dev/null 2>&1; then
        fail "runtime layer ships no JDK (javac must be absent)"
    else
        pass "runtime layer ships no JDK (javac absent)"
    fi
    if docker run --rm --entrypoint sh "$BACKEND_IMAGE" -c 'command -v mvn || test -d /root/.m2' >/dev/null 2>&1; then
        fail "runtime layer ships no Maven"
    else
        pass "runtime layer ships no Maven"
    fi
    assert_ok "runtime layer has a JRE (java present)" \
        docker run --rm --entrypoint sh "$BACKEND_IMAGE" -c 'java -version'

    # Non-root user.
    local whoami_out
    whoami_out=$(docker run --rm --entrypoint sh "$BACKEND_IMAGE" -c 'id -un' 2>/dev/null)
    if [[ "$whoami_out" == "reconx" ]]; then
        pass "container runs as non-root user 'reconx'"
    else
        fail "container runs as non-root user 'reconx' (got '${whoami_out:-none}')"
    fi

    assert_ok "/app/app.jar exists in the final image" \
        docker run --rm --entrypoint sh "$BACKEND_IMAGE" -c 'test -f /app/app.jar'

    # Layer caching: touching a source file must not re-resolve dependencies.
    local marker="backend/src/main/resources/.adv146-cache-probe"
    date +%s > "$marker"
    local start elapsed
    start=$(date +%s)
    docker build -q -f backend/Dockerfile -t "$BACKEND_IMAGE" . >/dev/null 2>&1
    local rebuild_status=$?
    elapsed=$(( $(date +%s) - start ))
    rm -f "$marker"
    if [[ $rebuild_status -eq 0 && $elapsed -lt 30 ]]; then
        pass "rebuild after a src/ edit took ${elapsed}s (< 30s, dependency layer cached)"
    else
        fail "rebuild after a src/ edit took ${elapsed}s (expected < 30s)"
    fi

    if [[ -f backend/.dockerignore ]]; then
        pass "backend/.dockerignore exists"
    else
        fail "backend/.dockerignore exists"
    fi
    # The root .dockerignore is the one BuildKit honours for a root-context build.
    local missing=()
    for pattern in 'target/' '.git' '.idea' 'node_modules'; do
        grep -q -- "$pattern" .dockerignore 2>/dev/null || missing+=("$pattern")
    done
    if [[ ${#missing[@]} -eq 0 ]]; then
        pass "root .dockerignore excludes build output, VCS and IDE dirs"
    else
        fail "root .dockerignore missing patterns: ${missing[*]}"
    fi
}

# -----------------------------------------------------------------------------
# TICKET-ADV147 — frontend image
# -----------------------------------------------------------------------------
verify_frontend() {
    group "TICKET-ADV147 — frontend multi-stage Dockerfile and nginx.conf"

    if docker build -q -t "$FRONTEND_IMAGE" frontend/ >/dev/null 2>&1; then
        pass "docker build frontend/ succeeds"
    else
        fail "docker build frontend/ succeeds"
        return
    fi

    local size
    size=$(image_size_mb "$FRONTEND_IMAGE")
    if [[ -n "$size" && "$size" -lt 100 ]]; then
        pass "final image is ${size} MB (< 100 MB — node_modules not shipped)"
    else
        fail "final image is ${size:-unknown} MB (expected < 100 MB)"
    fi

    assert_ok "runtime stage is nginx-based (nginx binary present)" \
        docker run --rm --entrypoint sh "$FRONTEND_IMAGE" -c 'nginx -v'
    if docker run --rm --entrypoint sh "$FRONTEND_IMAGE" -c 'command -v node' >/dev/null 2>&1; then
        fail "runtime stage is not node-based (node must be absent)"
    else
        pass "runtime stage is not node-based (node absent)"
    fi

    # Deep-link / SPA fallback against a live container.
    local cid
    cid=$(docker run -d --rm -p "${FRONTEND_PORT}:80" "$FRONTEND_IMAGE" 2>/dev/null)
    if [[ -z "$cid" ]]; then
        fail "frontend container starts on port ${FRONTEND_PORT}"
        return
    fi
    local attempt=0
    until curl -sf "http://localhost:${FRONTEND_PORT}/" >/dev/null 2>&1 || [[ $attempt -ge 15 ]]; do
        sleep 1; attempt=$((attempt + 1))
    done

    local root_status deep_status deep_body
    root_status=$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:${FRONTEND_PORT}/")
    deep_status=$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:${FRONTEND_PORT}/trades")
    deep_body=$(curl -s "http://localhost:${FRONTEND_PORT}/trades")

    [[ "$root_status" == "200" ]] && pass "GET / returns 200" || fail "GET / returns 200 (got $root_status)"
    if [[ "$deep_status" == "200" ]]; then
        pass "hard refresh on /trades returns 200 (SPA fallback, not 404)"
    else
        fail "hard refresh on /trades returns 200 (got $deep_status)"
    fi
    if grep -qi '<div id="root"' <<<"$deep_body" || grep -qi '<script' <<<"$deep_body"; then
        pass "/trades serves index.html markup"
    else
        fail "/trades serves index.html markup"
    fi

    docker stop "$cid" >/dev/null 2>&1

    # /api/ proxy must target the compose service name, never localhost.
    # Literal host, or a variable set to the compose service name (deferred DNS).
    if grep -Eq 'proxy_pass[[:space:]]+http://(backend|\$[a-z_]+):8080' frontend/nginx.conf \
       && grep -Eq '(proxy_pass[[:space:]]+http://backend|set[[:space:]]+\$[a-z_]+[[:space:]]+backend;)' frontend/nginx.conf; then
        pass "nginx.conf proxies /api/ to backend:8080 (same-origin, no CORS)"
    else
        fail "nginx.conf proxies /api/ to backend:8080"
    fi
    if grep -Eq 'proxy_pass[[:space:]]+http://(localhost|127\.0\.0\.1)' frontend/nginx.conf; then
        fail "nginx.conf contains no localhost proxy_pass target"
    else
        pass "nginx.conf contains no localhost proxy_pass target"
    fi
    if grep -q 'proxy_buffering off' frontend/nginx.conf; then
        pass "nginx.conf disables buffering for SSE"
    else
        fail "nginx.conf disables buffering for SSE"
    fi
}

# -----------------------------------------------------------------------------
# TICKET-ADV148 — compose stack
# -----------------------------------------------------------------------------
verify_compose_static() {
    group "TICKET-ADV148 — docker-compose.yml (static checks)"

    assert_ok "docker compose config is valid" docker compose config --quiet

    local services
    services=$(docker compose config --services 2>/dev/null | sort | tr '\n' ' ')
    local expected
    expected=$(printf '%s\n' "${EXPECTED_SERVICES[@]}" | sort | tr '\n' ' ')
    if [[ "$services" == "$expected" ]]; then
        pass "default profile declares exactly 7 services: $expected"
    else
        fail "default profile services mismatch — got: ${services:-none}"
    fi

    # backend gated on BOTH postgres and kafka being healthy
    local gates
    gates=$(python3 - <<'PY' 2>/dev/null
import subprocess, sys, yaml
out = subprocess.run(["docker","compose","config"], capture_output=True, text=True).stdout
cfg = yaml.safe_load(out) or {}
dep = (cfg.get("services", {}).get("backend", {}) or {}).get("depends_on", {}) or {}
ok = all(isinstance(dep.get(s), dict) and dep[s].get("condition") == "service_healthy"
         for s in ("postgres", "kafka"))
print("ok" if ok else "no")
PY
)
    if [[ "$gates" == "ok" ]]; then
        pass "backend depends_on postgres+kafka with condition: service_healthy"
    else
        fail "backend depends_on postgres+kafka with condition: service_healthy"
    fi

    # Every expected service must declare a healthcheck so `ps` can report healthy.
    local no_health
    no_health=$(python3 - <<'PY' 2>/dev/null
import subprocess, yaml
out = subprocess.run(["docker","compose","config"], capture_output=True, text=True).stdout
cfg = yaml.safe_load(out) or {}
missing = [n for n, s in (cfg.get("services") or {}).items() if not (s or {}).get("healthcheck")]
print(",".join(sorted(missing)))
PY
)
    if [[ -z "$no_health" ]]; then
        pass "every service declares a healthcheck"
    else
        fail "services without a healthcheck: $no_health"
    fi

    # No service-to-service URL may point at localhost. The host-facing Kafka
    # advertised listener and container-local healthcheck probes are exempt.
    local offenders
    offenders=$(python3 - <<'PY' 2>/dev/null
import subprocess, yaml
out = subprocess.run(["docker","compose","config"], capture_output=True, text=True).stdout
cfg = yaml.safe_load(out) or {}
bad = []
for name, svc in (cfg.get("services") or {}).items():
    for key, value in ((svc or {}).get("environment") or {}).items():
        if value is None:
            continue
        text = str(value)
        if "localhost" not in text and "127.0.0.1" not in text:
            continue
        # PLAINTEXT_HOST is the listener the developer's laptop connects to.
        if key == "KAFKA_ADVERTISED_LISTENERS" and "PLAINTEXT_HOST://localhost" in text:
            continue
        bad.append(f"{name}.{key}")
print(",".join(bad))
PY
)
    if [[ -z "$offenders" ]]; then
        pass "no localhost in any service-to-service URL"
    else
        fail "localhost found in service-to-service URLs: $offenders"
    fi

    if grep -Eq '^\s*context:\s*\.\s*$' <<<"$(sed -n '/backend:/,/frontend:/p' docker-compose.yml)"; then
        pass "backend build context is the repo root (recon-audit-starter reachable)"
    else
        fail "backend build context is the repo root (recon-audit-starter reachable)"
    fi
}

verify_compose_runtime() {
    group "TICKET-ADV148 — docker-compose.yml (live stack)"

    docker compose down --volumes --remove-orphans >/dev/null 2>&1
    if ! docker compose up -d --build >/tmp/adv148-up.log 2>&1; then
        fail "docker compose up -d --build succeeds (see /tmp/adv148-up.log)"
        docker compose down --volumes --remove-orphans >/dev/null 2>&1
        return
    fi
    pass "docker compose up -d --build succeeds"

    local waited=0 unhealthy=""
    while [[ $waited -lt $COMPOSE_TIMEOUT_SECONDS ]]; do
        unhealthy=""
        for service in "${EXPECTED_SERVICES[@]}"; do
            local cid state health
            cid=$(docker compose ps -q "$service" 2>/dev/null)
            if [[ -z "$cid" ]]; then unhealthy+="$service(missing) "; continue; fi
            state=$(docker inspect "$cid" --format '{{.State.Status}}' 2>/dev/null)
            health=$(docker inspect "$cid" --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' 2>/dev/null)
            [[ "$state" == "running" && "$health" == "healthy" ]] || unhealthy+="$service($state/$health) "
        done
        [[ -z "$unhealthy" ]] && break
        sleep "$COMPOSE_POLL_INTERVAL"
        waited=$((waited + COMPOSE_POLL_INTERVAL))
    done

    if [[ -z "$unhealthy" ]]; then
        pass "all 7 services reached running (healthy) in ${waited}s"
    else
        fail "services not healthy after ${COMPOSE_TIMEOUT_SECONDS}s: $unhealthy"
    fi

    # No restart loops.
    local restarts=""
    for service in "${EXPECTED_SERVICES[@]}"; do
        local cid count
        cid=$(docker compose ps -q "$service" 2>/dev/null) || continue
        [[ -z "$cid" ]] && continue
        count=$(docker inspect "$cid" --format '{{.RestartCount}}' 2>/dev/null)
        [[ "${count:-0}" -gt 0 ]] && restarts+="$service($count) "
    done
    if [[ -z "$restarts" ]]; then
        pass "no service entered a restart loop"
    else
        fail "services restarted: $restarts"
    fi

    # Frontend reaches the backend across the compose network (ADV147 + ADV148).
    local api_status
    api_status=$(docker compose exec -T frontend sh -c \
        'wget -q -S -O /dev/null http://backend:8080/api/actuator/health 2>&1 | head -1' 2>/dev/null)
    if grep -q '200' <<<"$api_status"; then
        pass "frontend reaches backend:8080 over the compose network"
    else
        fail "frontend reaches backend:8080 over the compose network (got: ${api_status:-no response})"
    fi

    if [[ "${KEEP_UP:-0}" != "1" ]]; then
        docker compose down --volumes --remove-orphans >/dev/null 2>&1
    fi
}

main() {
    local target="${1:-all}"
    case "$target" in
        backend)  verify_backend ;;
        frontend) verify_frontend ;;
        compose)  verify_compose_static; verify_compose_runtime ;;
        static)   verify_compose_static ;;
        all)      verify_backend; verify_frontend; verify_compose_static; verify_compose_runtime ;;
        *)        echo "usage: $0 [all|backend|frontend|compose|static]" >&2; exit 2 ;;
    esac

    printf '\n\033[1m%d passed, %d failed\033[0m\n' "$PASSED" "$FAILED"
    [[ $FAILED -eq 0 ]]
}

main "$@"
