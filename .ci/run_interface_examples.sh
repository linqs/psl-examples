#!/bin/bash

# Run an instance of each PSL interface.

readonly THIS_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
readonly EXAMPLE_DIR="${THIS_DIR}/../simple-acquaintances"

function main() {
    if [[ $# -ne 0 ]] ; then
        echo "USAGE: $0"
        exit 1
    fi

    trap exit SIGINT
    set -e

    # Remove cached jar.
    rm -f "${EXAMPLE_DIR}"/cli/*.jar

    for runScript in "${EXAMPLE_DIR}/"*"/run.sh" ; do
        "${runScript}" --postgres psltest -D log4j.threshold=DEBUG -D votedperceptron.numsteps=1 -D admmreasoner.maxiterations=10
    done
}

[[ "${BASH_SOURCE[0]}" == "${0}" ]] && main "$@"
