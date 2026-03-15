#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

if [[ ! -f cp.txt || "${1:-}" == "--refresh-cp" ]]; then
  echo "Generating cp.txt from Maven dependencies..."
  mvn -q dependency:build-classpath -Dmdep.outputFile=cp.txt
fi

rm -rf out
mkdir -p out

echo "Compiling Java sources to out/..."
javac -d out -cp "$(cat cp.txt)" --release 21 $(find src/main/java -name "*.java")

echo "Compilation successful. Classes are in out/."
