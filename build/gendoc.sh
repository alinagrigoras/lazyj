#!/bin/bash

cd `dirname $0`/../src

mkdir ../doc &>/dev/null

export JAVA_HOME=${JAVA_HOME:-/usr}

find lazyj -type f -name \*.java | xargs $JAVA_HOME/bin/javadoc \
    -d ../doc \
    -classpath ../lib/jsdk.jar:../lib/oreilly.jar:../lib/mail.jar:../lib/ymsg.jar \
    -protected \
    -keywords \
    -windowtitle "LazyJ Web Templates API" \
    -doctitle "LazyJ API" \
    -bottom "<script src=\"http://www.google-analytics.com/urchin.js\" type=\"text/javascript\"></script><script type=\"text/javascript\">_uacct = \"UA-2970758-1\";urchinTracker();</script>" \
    -version \
    -author \
    -use
