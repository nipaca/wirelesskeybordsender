#!/bin/sh

#
# Gradle start up script for POSIX
#

APP_NAME="Gradle"
APP_BASE_NAME=`basename "$0"`
APP_HOME="`pwd -P`"

# Resolve symlinks
while [ -h "$APP_HOME/$0" ]; do
    ls=`ls -ld "$APP_HOME/$0"`
    link=`expr "$ls" : '.*-> \(.*\)$'`
    if expr "$link" : '/.*' > /dev/null; then
        APP_HOME=`dirname "$link"`
    else
        APP_HOME=`dirname "$APP_HOME"`/"$link"
    fi
done

# Add default JVM options here
DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'

# Determine the Java command
if [ -n "$JAVA_HOME" ] ; then
    if [ -x "$JAVA_HOME/jre/sh/java" ] ; then
        JAVACMD="$JAVA_HOME/jre/sh/java"
    else
        JAVACMD="$JAVA_HOME/bin/java"
    fi
else
    JAVACMD="java"
fi

# Setup CLASSPATH
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

# Execute Gradle
eval "\"$JAVACMD\" $DEFAULT_JVM_OPTS -classpath \"$CLASSPATH\" org.gradle.wrapper.GradleWrapperMain \"$@\""
