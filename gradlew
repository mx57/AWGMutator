#!/bin/sh

#
# Copyright © 2015-2024 Gradle Inc.
# Gradle startup script for POSIX
#

# Attempt to set APP_HOME
app_path=$0

# Need this for relative symlinks.
while [ -h "$app_path" ] ; do
    ls=`ls -ld "$app_path"`
    link=`expr "$ls" : '.*-> \(.*\)$'`
    if expr "$link" : '/.*' > /dev/null; then
        app_path="$link"
    else
        app_path=`dirname "$app_path"`"/$link"
    fi
done

APP_HOME=`dirname "$app_path"`
APP_HOME=`cd "$APP_HOME" && pwd`
APP_NAME="Gradle"
APP_BASE_NAME=`basename "$0"`

DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'
MAX_FD=maximum

warn () {
    echo "$*"
} >&2

die () {
    echo
    echo "$*"
    echo
    exit 1
} >&2

CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

# Determine the Java command to use to start the JVM.
if [ -n "$JAVA_HOME" ] ; then
    if [ -x "$JAVA_HOME/jre/sh/java" ] ; then
        JAVACMD=$JAVA_HOME/jre/sh/java
    else
        JAVACMD=$JAVA_HOME/bin/java
    fi
else
    JAVACMD=java
fi

# Auto-download gradle-wrapper.jar if missing
if [ ! -f "$CLASSPATH" ] ; then
    mkdir -p "$APP_HOME/gradle/wrapper"
    if command -v curl >/dev/null 2>&1 ; then
        curl -sLo "$CLASSPATH" https://raw.githubusercontent.com/gradle/gradle/v8.11.1/gradle/wrapper/gradle-wrapper.jar 2>/dev/null || true
    elif command -v wget >/dev/null 2>&1 ; then
        wget -qO "$CLASSPATH" https://raw.githubusercontent.com/gradle/gradle/v8.11.1/gradle/wrapper/gradle-wrapper.jar 2>/dev/null || true
    fi
fi

# Execute Gradle
if [ -f "$CLASSPATH" ] ; then
    exec "$JAVACMD" "-Dorg.gradle.appname=$APP_BASE_NAME" -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
elif command -v gradle >/dev/null 2>&1 ; then
    exec gradle "$@"
else
    die "Could not locate gradle-wrapper.jar or system gradle."
fi
