#!/bin/sh

basedir=$(dirname "$0")

cd $basedir && \
java -Xmx512M -cp target:lib/ECLA.jar:lib/DTNConsoleConnection.jar core.DTNSim $*