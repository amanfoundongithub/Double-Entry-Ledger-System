#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
backend_pid=""
frontend_pid=""

cleanup() {
  trap - EXIT INT TERM
  if [[ -n "$backend_pid" ]]; then
    kill "$backend_pid" 2>/dev/null || true
  fi
  if [[ -n "$frontend_pid" ]]; then
    kill "$frontend_pid" 2>/dev/null || true
  fi
  if [[ -n "$backend_pid" ]]; then
    wait "$backend_pid" 2>/dev/null || true
  fi
  if [[ -n "$frontend_pid" ]]; then
    wait "$frontend_pid" 2>/dev/null || true
  fi
}

trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

(
  cd "$ROOT_DIR/ledger_service"
  exec ./gradlew bootRun
) &
backend_pid=$!

(
  cd "$ROOT_DIR/ledger-core-ui"
  exec npm start
) &
frontend_pid=$!

printf 'Backend:  http://localhost:5555\n'
printf 'Frontend: http://localhost:4200\n'
printf 'Press Ctrl+C to stop both services.\n'

while :; do
  running_jobs="$(jobs -pr)"

  if ! printf '%s\n' "$running_jobs" | grep -Fxq "$backend_pid"; then
    if wait "$backend_pid"; then
      status=0
    else
      status=$?
    fi
    printf 'Backend exited (status %s); stopping frontend.\n' "$status" >&2
    exit "$status"
  fi

  if ! printf '%s\n' "$running_jobs" | grep -Fxq "$frontend_pid"; then
    if wait "$frontend_pid"; then
      status=0
    else
      status=$?
    fi
    printf 'Frontend exited (status %s); stopping backend.\n' "$status" >&2
    exit "$status"
  fi

  sleep 1
done
