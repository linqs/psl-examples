#!/bin/bash

# The run script for the python interface is very simple.
# It esentially just ensures that the data exists and runs the python script.
# User's should ensure that the `pslpython` python package is installed.

readonly BASE_NAME='friendship'
readonly FETCH_DATA_SCRIPT='../data/fetchData.sh'

function main() {
   trap exit SIGINT

   getData
   check_requirements
   run
}

function getData() {
   pushd . > /dev/null

   cd "$(dirname $FETCH_DATA_SCRIPT)"
   bash "$(basename $FETCH_DATA_SCRIPT)"

   popd > /dev/null
}

function run() {
   echo "Running PSL"

   python3 "${BASE_NAME}.py"
   if [[ "$?" -ne 0 ]]; then
      echo 'ERROR: Failed to run'
      exit 60
   fi
}

function check_requirements() {
   type python3 > /dev/null 2> /dev/null
   if [[ "$?" -ne 0 ]]; then
      echo 'ERROR: python3 required to run project'
      exit 10
   fi
}

main "$@"
