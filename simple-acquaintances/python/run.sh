#!/bin/bash

readonly BASE_NAME='simple-acquaintances'

function main() {
   trap exit SIGINT

   check_requirements
   run
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
