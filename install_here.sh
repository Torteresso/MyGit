#!/bin/bash

currentdir=$(pwd)

cd $(dirname $0)
gradle -q clean distZip
unzip -q cli-app/build/distributions/mgit-dist.zip -d $currentdir
cd $currentdir
ln -sf mgit-dist/bin/mgit-dist mgit
