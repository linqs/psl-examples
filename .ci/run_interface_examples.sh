#!/bin/bash

# Run an instance of each PSL interface.

readonly THIS_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
readonly BASE_DIR="${THIS_DIR}/.."

function main() {
    if [[ $# -ne 0 ]] ; then
        echo "USAGE: $0"
        exit 1
    fi

    trap exit SIGINT
    set -e

    # TODO(eriq): Soon we will enable all interfaces, but only do CLI now.
    cd "${BASE_DIR}/simple-acquaintances/cli"
    ./run.sh --postgres psltest -D log4j.threshold=DEBUG -D votedperceptron.numsteps=1 -D admmreasoner.maxiterations=10
}

[[ "${BASH_SOURCE[0]}" == "${0}" ]] && main "$@"
