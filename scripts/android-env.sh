#!/usr/bin/env bash

set -e

JAVA21_HOME=$(/usr/libexec/java_home -v 21 2>/dev/null || true)

if [[ -z "$JAVA21_HOME" ]]; then
  echo "ERROR: JDK 21 tidak ditemukan."
  exit 1
fi

export JAVA_HOME="$JAVA21_HOME"
export PATH="$JAVA_HOME/bin:$PATH"

if [[ -d "$HOME/Library/Android/sdk" ]]; then
  export ANDROID_HOME="$HOME/Library/Android/sdk"
  export ANDROID_SDK_ROOT="$ANDROID_HOME"
  export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"
fi
