#!/bin/bash

# Run all available run scripts.

readonly THIS_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
readonly BASE_DIR="${THIS_DIR}/.."

# Skip these examples because of the amount of memory necessary on the CI machine.
readonly SKIP_EXAMPLES="imdb-er lastfm yelp"

function main() {
    if [[ $# -ne 0 ]] ; then
        echo "USAGE: $0"
        exit 1
    fi

    trap exit SIGINT
    set -e

    # Remove all cached jars.
    rm -f "${BASE_DIR}"/*/cli/*.jar

    for runPath in "${BASE_DIR}/"*/*"/run.sh" ; do
        local exampleName=$(basename $(dirname $(dirname "${runPath}")))
        local exampleType=$(basename $(dirname "${runPath}"))

        if [[ "${SKIP_EXAMPLES}" == *"${exampleName}"* ]] ; then
            echo "Skipping ${exampleName} (${exampleType}) due to memory requirements."
            continue
        fi

        echo "Running ${exampleName} (${exampleType})."
        # Note that not all interfaces will honor the passed arguments.
        "${runPath}" --postgres psltest -D log4j.threshold=DEBUG -D votedperceptron.numsteps=1 -D admmreasoner.maxiterations=10 -D randomgridsearch.maxlocations=10 -D continuousrandomgridsearch.maxlocations=10 -D gpp.maxiterations=5
    done
}

[[ "${BASH_SOURCE[0]}" == "${0}" ]] && main "$@"
