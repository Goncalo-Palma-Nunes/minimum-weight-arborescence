#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

mvn -q dependency:build-classpath -Dmdep.outputFile=cp.txt

if [[ ! -f cp.txt ]]; then
  echo "Error: cp.txt not found in $ROOT_DIR"
  echo "Create it with: mvn -q dependency:build-classpath -Dmdep.outputFile=cp.txt"
  exit 1
fi

mkdir -p target/classes

mapfile -t sources < <(find src/main/java -type f -name "*.java" | sort)
if [[ ${#sources[@]} -eq 0 ]]; then
  echo "Error: no Java sources found under src/main/java"
  exit 1
fi

javac --release 21 -encoding UTF-8 \
  -cp "$(cat cp.txt)" \
  -d target/classes \
  "${sources[@]}"

echo "Compilation finished: target/classes"
