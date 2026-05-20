#!/usr/bin/env bash
# =============================================================================
# Nacos Stress Test Runner
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
JAR_NAME="nacos-stresstest-1.0.0.jar"
JAR_PATH="$PROJECT_DIR/target/$JAR_NAME"

# --- Find Java ---
if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    JAVA="$JAVA_HOME/bin/java"
elif command -v java &>/dev/null; then
    JAVA="$(command -v java)"
else
    echo "ERROR: Java not found. Set JAVA_HOME or add java to PATH." >&2
    exit 1
fi

# --- Check Java version (minimum 11) ---
JAVA_VERSION=$("$JAVA" -version 2>&1 | head -1 | sed -E 's/.*"([0-9]+)(\.[0-9]+)*.*/\1/')
if [ "$JAVA_VERSION" -lt 11 ]; then
    echo "ERROR: Java 11+ required, found Java $JAVA_VERSION." >&2
    exit 1
fi

# --- Find JAR ---
if [ ! -f "$JAR_PATH" ]; then
    echo "ERROR: $JAR_NAME not found at $JAR_PATH" >&2
    echo "       Run 'mvn clean package' in $PROJECT_DIR first." >&2
    exit 1
fi

# --- Run ---
echo "Using Java $JAVA_VERSION: $JAVA"
echo "Running: $JAR_PATH"
echo "---"
exec "$JAVA" -jar "$JAR_PATH" "$@"
