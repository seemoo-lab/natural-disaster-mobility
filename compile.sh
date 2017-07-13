#!/bin/sh

basedir=$(dirname "$0")
srcdir="${basedir}/src"
libdir="${basedir}/lib"
targetdir="${basedir}/target"

if [ ! -d "$targetdir" ]; then mkdir $targetdir; fi

javac -sourcepath $srcdir -d $targetdir -extdirs $libdir $srcdir/core/*.java $srcdir/movement/*.java $srcdir/report/*.java $srcdir/routing/*.java $srcdir/gui/*.java $srcdir/input/*.java $srcdir/applications/*.java $srcdir/interfaces/*.java

if [ ! -d "$targetdir/gui/buttonGraphics" ]; then cp -R $srcdir/gui/buttonGraphics $targetdir/gui/; fi
