#!/bin/sh
JAVA_HOME="c:/Android/jdk17/jdk-17.0.13+11"
exec "$JAVA_HOME/bin/java" -cp "$(dirname "$0")/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
