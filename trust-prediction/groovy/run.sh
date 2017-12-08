#!/bin/bash

readonly BASE_NAME='trustprediction'
readonly CLASSPATH_FILE='classpath.out'
readonly FETCH_DATA_SCRIPT='../data/fetchData.sh'
readonly TARGET_CLASS="org.linqs.psl.examples.${BASE_NAME}.Run"

function main() {
   trap exit SIGINT

   getData

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

function getData() {
   pushd . > /dev/null

   cd "$(dirname $FETCH_DATA_SCRIPT)"
   bash "$(basename $FETCH_DATA_SCRIPT)"

   popd > /dev/null
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
   if [ -e "${CLASSPATH_FILE}" ]; then
      echo "Classpath found cached, skipping classpath build."
      return
   fi

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
