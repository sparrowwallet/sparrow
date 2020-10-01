#!/usr/bin/env sh

args="$*"
args="${args%"${args##*[![:space:]]}"}"

if [ -n "$args" ]
then
  ./gradlew run --args="$args"
else
  ./gradlew run
fi
