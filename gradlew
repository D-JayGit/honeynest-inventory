#!/usr/bin/env sh

set -eu

APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
WRAPPER_URL="https://services.gradle.org/distributions/gradle-9.3.1-wrapper.jar"
WRAPPER_SHA256="b3a875ddc1f044746e1b1a55f645584505f4a10438c1afea9f15e92a7c42ec13"

if [ ! -f "$WRAPPER_JAR" ]; then
  echo "Downloading the Gradle wrapper runtime..."
  mkdir -p "$(dirname "$WRAPPER_JAR")"
  if command -v curl >/dev/null 2>&1; then
    curl --fail --location --output "$WRAPPER_JAR" "$WRAPPER_URL"
  elif command -v wget >/dev/null 2>&1; then
    wget --output-document="$WRAPPER_JAR" "$WRAPPER_URL"
  else
    echo "curl or wget is required to download the Gradle wrapper runtime." >&2
    exit 1
  fi

  if command -v sha256sum >/dev/null 2>&1; then
    ACTUAL_SHA256=$(sha256sum "$WRAPPER_JAR" | awk '{print $1}')
  else
    ACTUAL_SHA256=$(shasum -a 256 "$WRAPPER_JAR" | awk '{print $1}')
  fi
  if [ "$ACTUAL_SHA256" != "$WRAPPER_SHA256" ]; then
    rm -f "$WRAPPER_JAR"
    echo "Gradle wrapper checksum verification failed." >&2
    exit 1
  fi
fi

exec "${JAVA_HOME:+$JAVA_HOME/bin/}java" -classpath "$WRAPPER_JAR" org.gradle.wrapper.GradleWrapperMain "$@"
