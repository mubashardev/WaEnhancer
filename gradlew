#!/bin/sh
# SPDX-License-Identifier: GPL-3.0-only
# Copyright (C) 2026 WaEnhancer contributors

set -eu

APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
JAVA_COMMAND=${JAVA_HOME:+$JAVA_HOME/bin/}java

exec "$JAVA_COMMAND" \
  ${JAVA_OPTS:-} \
  ${GRADLE_OPTS:-} \
  -Dorg.gradle.appname=gradlew \
  -classpath "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" \
  org.gradle.wrapper.GradleWrapperMain "$@"
