#!/bin/bash

cd `dirname $0`/..

CP=src

for jar in lib/*.jar; do
    CP="$CP:$jar"
done

export JAVA_HOME=${JAVA_HOME:-/usr}

find src -type f -name \*.java | xargs $JAVA_HOME/bin/javac -g -O -classpath "$CP" -d build/classes && build/build.sh
