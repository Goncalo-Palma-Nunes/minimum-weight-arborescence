#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

MAIN_CLASS="${1:-optimalarborescence.Main}"
shift || true

if [[ ! -d out ]]; then
  echo "Missing out/ directory. Run ./compile.sh first." >&2
  exit 1
fi

if [[ ! -f cp.txt ]]; then
  echo "Missing cp.txt. Generating with Maven..."
  mvn -q dependency:build-classpath -Dmdep.outputFile=cp.txt
fi

java -cp "out:$(cat cp.txt)" "$MAIN_CLASS" "$@"
