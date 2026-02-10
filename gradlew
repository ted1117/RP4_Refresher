#!/bin/sh

# Minimal Gradle wrapper script (POSIX)

APP_BASE_NAME=${0##*/}
APP_HOME=$(cd "$(dirname "$0")" && pwd -P) || exit 1

CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

if [ -n "$JAVA_HOME" ]; then
    JAVA_CMD="$JAVA_HOME/bin/java"
    if [ ! -x "$JAVA_CMD" ]; then
        echo "ERROR: JAVA_HOME is set to an invalid directory: $JAVA_HOME" >&2
        echo "Please set JAVA_HOME to a valid JDK path." >&2
        exit 1
    fi
else
    JAVA_CMD=$(command -v java)
    if [ -z "$JAVA_CMD" ]; then
        echo "ERROR: JAVA_HOME is not set and 'java' was not found in PATH." >&2
        echo "Please set JAVA_HOME to a valid JDK path." >&2
        exit 1
    fi
fi

DEFAULT_JVM_OPTS='-Xmx64m -Xms64m'

# shellcheck disable=SC2086
set -- $DEFAULT_JVM_OPTS $JAVA_OPTS $GRADLE_OPTS \
    "-Dorg.gradle.appname=$APP_BASE_NAME" \
    -classpath "$CLASSPATH" \
    org.gradle.wrapper.GradleWrapperMain \
    "$@"

exec "$JAVA_CMD" "$@"
