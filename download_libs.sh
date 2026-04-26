#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

mkdir -p lib

fetch_jar() {
  local group="$1"
  local artifact="$2"
  local version="$3"
  local group_path="${group//./\/}"
  local file="${artifact}-${version}.jar"
  local url="https://repo1.maven.org/maven2/${group_path}/${artifact}/${version}/${file}"

  if [[ -f "lib/${file}" ]]; then
    echo "Already present: ${file}"
    return
  fi

  echo "Downloading ${file}"
  curl -fsSL "$url" -o "lib/${file}"
}

# Runtime dependencies needed to compile src/main/java without Maven.
fetch_jar org.apache.commons commons-math3 3.6.1
fetch_jar com.opencsv opencsv 5.9
fetch_jar org.apache.commons commons-lang3 3.13.0
fetch_jar org.apache.commons commons-text 1.11.0
fetch_jar commons-beanutils commons-beanutils 1.9.4
fetch_jar commons-logging commons-logging 1.2
fetch_jar commons-collections commons-collections 3.2.2
fetch_jar org.apache.commons commons-collections4 4.4
fetch_jar com.esotericsoftware kryo 5.6.2
fetch_jar com.esotericsoftware reflectasm 1.11.9
fetch_jar org.objenesis objenesis 3.4
fetch_jar com.esotericsoftware minlog 1.3.1
fetch_jar org.json json 20240303

echo "Dependencies downloaded into lib/."
