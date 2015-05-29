#!/bin/bash
# merges Qquill.jar into Qquil-all.jar
mkdir tmp

cd tmp/
cp ../Qquill-all.jar .
jar xf Qquill-all.jar
rm Qquill-all.jar
rm -rf com/gradians

cp ../Qquill.jar .
jar xf Qquill.jar
rm Qquill.jar

jar cfe Qquill-all.jar com.gradians.pipeline.Driver *
cp Qquill-all.jar ../
cd ..
rm -rf tmp/
