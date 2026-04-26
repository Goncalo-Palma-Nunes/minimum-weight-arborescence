#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

if [[ $# -lt 1 ]]; then
  echo "Usage: ./run.sh <main-class> [args...]"
  exit 1
fi

if [[ ! -d target/classes ]]; then
  echo "Error: target/classes not found. Run ./compile.sh first."
  exit 1
fi

if [[ ! -f cp.txt ]]; then
  echo "Error: cp.txt not found in $ROOT_DIR"
  echo "Create it with: mvn -q dependency:build-classpath -Dmdep.outputFile=cp.txt"
  exit 1
fi

exec java -cp "target/classes:$(cat cp.txt)" "$@"
