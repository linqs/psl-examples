#!/bin/bash

# The run script for the python interface is very simple.
# It esentially just ensures that the data exists and runs the python script.
# User's should ensure that the `pslpython` python package is installed.

readonly THIS_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

readonly BASE_NAME='simple-acquaintances'
readonly RUN_SCRIPT_VERSION='1.1.0'

function main() {
   trap exit SIGINT

   cd "${THIS_DIR}"

   check_requirements
   run
}

function run() {
   echo "Running PSL."

   python3 "${BASE_NAME}.py"
   if [[ "$?" -ne 0 ]]; then
      echo 'ERROR: Failed to run.'
      exit 60
   fi
}

function check_requirements() {
   type python3 > /dev/null 2> /dev/null
   if [[ "$?" -ne 0 ]]; then
      echo 'ERROR: python3 required to run project.'
      exit 10
   fi
}

[[ "${BASH_SOURCE[0]}" == "${0}" ]] && main "$@"
