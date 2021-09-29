#!/bin/bash

# Run the CLI interface for all examples.

readonly THIS_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
readonly BASE_DIR="${THIS_DIR}/.."

function main() {
    if [[ $# -ne 0 ]] ; then
        echo "USAGE: $0"
        exit 1
    fi

    trap exit SIGINT
    set -e

    for runPath in "${BASE_DIR}/"*"/cli/run.sh" ; do
        "${runPath}" --postgres psltest -D log4j.threshold=DEBUG -D votedperceptron.numsteps=1 -D admmreasoner.maxiterations=10
    done
}

[[ "${BASH_SOURCE[0]}" == "${0}" ]] && main "$@"
