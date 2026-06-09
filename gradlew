#!/usr/bin/env sh
set -e
DIR="$(cd "$(dirname "$0")" && pwd)"
GRADLE_VERSION="8.11.1"
GRADLE_HOME="$DIR/.gradle/redplus-gradle/gradle-$GRADLE_VERSION"
GRADLE_BIN="$GRADLE_HOME/bin/gradle"
if [ -f "$DIR/gradle/wrapper/gradle-wrapper.jar" ]; then
  exec java -jar "$DIR/gradle/wrapper/gradle-wrapper.jar" "$@"
fi
if [ ! -x "$GRADLE_BIN" ]; then
  echo "Gradle was not found in the project. Downloading Gradle $GRADLE_VERSION locally..." >&2
  mkdir -p "$DIR/.gradle/redplus-gradle"
  if command -v curl >/dev/null 2>&1; then
    curl -L "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" -o "$DIR/.gradle/gradle-$GRADLE_VERSION-bin.zip"
  elif command -v wget >/dev/null 2>&1; then
    wget "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" -O "$DIR/.gradle/gradle-$GRADLE_VERSION-bin.zip"
  else
    echo "Install Gradle $GRADLE_VERSION+ or curl/wget, then run again." >&2
    exit 1
  fi
  unzip -o "$DIR/.gradle/gradle-$GRADLE_VERSION-bin.zip" -d "$DIR/.gradle/redplus-gradle" >/dev/null
fi
exec "$GRADLE_BIN" "$@"
