#!/bin/sh

echo "[START] one.sh $*"

basedir=$(dirname "$0")
cd $basedir && \
java -Xmx512M -cp target:lib/ECLA.jar:lib/DTNConsoleConnection.jar core.DTNSim $*

echo "[FINISH] one.sh $*"
