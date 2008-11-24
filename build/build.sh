#!/bin/bash

cd `dirname $0`/classes

$JAVA_HOME/bin/jar -cmf ../manifest ../lazyj.jar lazyj || exit 1
cd ../..
find src -name \*.java -o -name \*.html | xargs $JAVA_HOME/bin/jar -uf build/lazyj.jar || exit 1
mv build/lazyj.jar lib/ || exit 1
cd lib
$JAVA_HOME/bin/jar -i lazyj.jar || exit 1
mv lazyj.jar ../build
