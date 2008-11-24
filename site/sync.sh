#!/bin/bash

cd `dirname $0`

TARGET="costing,lazyj@web.sourceforge.net:htdocs"
RSYNC="rsync -vrlIz --size-only --exclude=.svn -e ssh"

$RSYNC . $TARGET/
$RSYNC ../doc/ $TARGET/doc/
$RSYNC ../build/lazyj.jar $TARGET/download/
