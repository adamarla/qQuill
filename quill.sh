#!/bin/bash

function main() {

    if [ -z "$1" ] ; then
        #echo $2
        usage
    else 
        if [ "$1" == "-u" ] ; then
            update $2
        else
            if [ "$1" == "-d" ] ; then
                download $2
            else
                run_quill $1 $2
            fi
        fi
    fi
}

function usage() {

    echo "Usage: quill [-u|-d] | [-p|-r|-e|-t] dir"
    echo "========================================"
    echo " Download Options:"
    echo "-u update quill (in current dir)"
    echo "-d download quill (into current dir)"
    echo "----------------------------------------"
    echo " Run Options:"
    echo "-e Editor edit/modify question"
    echo "-t Tagger tag folders"
    echo "-p Preview view generated SVGs"
    echo "-r Render generated SVGs for mobile app"
    echo ""
}

function run_quill() {

    dir=/home/adamarla/workspace/Qquill/build/libs
    if [ ! -f $dir/Qquill-all.jar ]; then
        echo "edit quill.sh: line 39 to specify location of Qquill-all.jar"
        return
    fi
    java -jar $dir/Qquill-all.jar $1 $2
}

function download() {

    if [ -z $1 ] ; then
        dir=.
    else
        if [ ! -e $1 ] ; then
            mkdir $1
        fi
        dir=$1
    fi
    curl "http://109.74.201.62:8080/quill/Qquill-all.jar" > $dir/Qquill-all.jar
}

function update() {
  
    if [ -z $1 ] ; then
        dir=.
    else
        dir=$1
    fi

    if [ ! -f $dir/Qquill-all.jar ]; then
        echo "First download Qquill-all.jar using -d option"
        return
    fi

    curl "http://109.74.201.62:8080/quill/Qquill.jar" > $dir/Qquill.jar
    echo "applying patch..."
    cd $dir

    mkdir tmp
    cd tmp/
    cp ../Qquill-all.jar .
    jar xf Qquill-all.jar
    rm Qquill-all.jar
    rm -rf com/gradians

    mv ../Qquill.jar .
    jar xf Qquill.jar
    rm Qquill.jar

    jar cfe Qquill-all.jar com.gradians.pipeline.Driver *
    cp Qquill-all.jar ../
    cd ..
    rm -rf tmp/
    echo "updated"
}

main $1 $2

