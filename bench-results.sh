#!/bin/sh
DATA_ROOT=~/benchmark-results/fork-join
mkdir -p $DATA_ROOT

REV=`git rev-parse HEAD | cut -c1-8`

NUM_PREV=`ls -l $DATA_ROOT | grep ^d | wc -l`
NUM_PREV=`printf "%03d" $NUM_PREV`

TARGET_PATH=$DATA_ROOT/$NUM_PREV-$REV
LATEST=$DATA_ROOT/latest

mkdir -p $TARGET_PATH
cp codespeed.data $TARGET_PATH/
rm $LATEST
ln -s $TARGET_PATH $LATEST