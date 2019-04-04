#!/bin/bash

readonly BASE_NAME='friendship'
readonly CLASSPATH_FILE='classpath.out'
readonly TARGET_CLASS="org.linqs.psl.examples.${BASE_NAME}.Run"

function main() {
   trap exit SIGINT

   check_requirements
   compile
   buildClasspath
   run
}

function run() {
   echo "Running PSL"

   java -cp ./target/classes:$(cat ${CLASSPATH_FILE}) ${TARGET_CLASS}
   if [[ "$?" -ne 0 ]]; then
      echo 'ERROR: Failed to run'
      exit 60
   fi
}

function check_requirements() {
   type mvn > /dev/null 2> /dev/null
   if [[ "$?" -ne 0 ]]; then
      echo 'ERROR: maven required to build project'
      exit 10
   fi

   type java > /dev/null 2> /dev/null
   if [[ "$?" -ne 0 ]]; then
      echo 'ERROR: java required to run project'
      exit 11
   fi
}

function buildClasspath() {
   # Rebuild every time.
   # It is hard for new users to know when to rebuild.

   mvn dependency:build-classpath -Dmdep.outputFile="${CLASSPATH_FILE}"
   if [[ "$?" -ne 0 ]]; then
      echo 'ERROR: Failed to build classpath'
      exit 50
   fi
}

function compile() {
   mvn compile
   if [[ "$?" -ne 0 ]]; then
      echo 'ERROR: Failed to compile'
      exit 40
   fi
}

main "$@"
