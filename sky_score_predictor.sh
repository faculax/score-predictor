#!/usr/bin/env bash
set -euo pipefail

# Run the Bundesliga scraper: builds the jar, then runs it.
# Usage:
#   ./sky_score_predictor.sh             # list-mode: fetch 9 URLs and print grouped odds for each
#   ./sky_score_predictor.sh <EVENT_URL> # scrape a single event URL

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "Building Bundesliga scraper (skip tests)..."
mvn -q -DskipTests clean package

JAR="target/bundesliga-scraper-1.0.0.jar"
if [[ ! -f "$JAR" ]]; then
  echo "Jar not found: $JAR" >&2
  exit 1
fi

if [[ $# -gt 0 ]]; then
  java -jar "$JAR" "$@"
else
  java -jar "$JAR"
fi
