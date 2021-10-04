#!/usr/bin/env sh

cd `dirname $0`
args="$*"
args="${args%"${args##*[![:space:]]}"}"

if [ -n "$args" ]
then
  ./gradlew run --args="$args"
else
  ./gradlew run
fi
