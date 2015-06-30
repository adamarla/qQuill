#!/bin/bash

function main() {
    if [ "$1" == "-u" ]
    then
        merge()
        echo "update"
    else
    fi
}

function merge() {
    curl "http://109.74.201.62:8080/Qquill.jar" > Qquill.jar

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
}

main
