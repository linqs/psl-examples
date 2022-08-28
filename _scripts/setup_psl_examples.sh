#!/bin/bash

# Fetch all the PSL examples and modify the CLI configuration for these experiments.
# Note that you can change the version of PSL used with the PSL_VERSION option here.
# This is a good script to start with when setting up experiments.

# Basic configuration options.
readonly PSL_VERSION='3.0.0-SNAPSHOT'

readonly BASE_DIR=$(realpath "$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"/..)

readonly PSL_EXAMPLES_DIR="${BASE_DIR}/psl-examples"
readonly PSL_EXAMPLES_REPO='https://github.com/linqs/psl-examples.git'
readonly PSL_EXAMPLES_BRANCH='main'

AVAILABLE_MEM_KB=$((8 * 1024 * 1024))
if [[ -e /proc/meminfo ]]; then
    AVAILABLE_MEM_KB=$(cat /proc/meminfo | grep 'MemTotal' | sed -E 's/^[^0-9]+([0-9]+)[^0-9]+$/\1/')
elif [[ $OSTYPE == 'darwin'*  || -x "$(command -v sysctl)" ]]; then
    AVAILABLE_MEM_KB=$(echo $(($(sysctl -n hw.memsize) / 1024)))
fi

# Reserve 4 GB.
readonly JAVA_MEM_GB=$((${AVAILABLE_MEM_KB} / 1024 / 1024 - 4))

function fetch_psl_examples() {
   if [ -e ${PSL_EXAMPLES_DIR} ]; then
      return
   fi

   git clone ${PSL_EXAMPLES_REPO} ${PSL_EXAMPLES_DIR}

   pushd . > /dev/null
      cd "${PSL_EXAMPLES_DIR}"
      git checkout ${PSL_EXAMPLES_BRANCH}
   popd > /dev/null
}

# Common to all examples.
function standard_fixes() {
    for exampleDir in `find ${PSL_EXAMPLES_DIR} -maxdepth 1 -mindepth 1 -type d -not -name '.*' -not -name '_*'`; do
        local baseName=`basename ${exampleDir}`

        pushd . > /dev/null
            cd "${exampleDir}/cli"

            # Increase memory allocation.
            sed -i "s/java -jar/java -Xmx${JAVA_MEM_GB}G -Xms${JAVA_MEM_GB}G -jar/" run.sh

            # Set the PSL version.
            sed -i "s/^readonly PSL_VERSION='.*'$/readonly PSL_VERSION='${PSL_VERSION}'/" run.sh
        popd > /dev/null
    done
}

function fetch_data() {
    for exampleDir in `find ${PSL_EXAMPLES_DIR} -maxdepth 1 -mindepth 1 -type d -not -name '.*' -not -name '_*'`; do
        local fetchScript="${exampleDir}/data/fetchData.sh"
        if [[ ! -f "${fetchScript}" ]] ; then
            continue
        fi

        bash "${fetchScript}"
    done
}

function main() {
   trap exit SIGINT

   fetch_psl_examples
   standard_fixes
   fetch_data

   exit 0
}

[[ "${BASH_SOURCE[0]}" == "${0}" ]] && main "$@"
