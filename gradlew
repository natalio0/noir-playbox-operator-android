#!/bin/sh
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

if [ ! -f "$CLASSPATH" ]; then
  echo "gradle-wrapper.jar belum ada."
  echo "Buka project ini di Android Studio terlebih dahulu dan Sync Project."
  echo "Jika Android Studio meminta Gradle, gunakan Gradle 8.9."
  exit 1
fi

exec java -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
