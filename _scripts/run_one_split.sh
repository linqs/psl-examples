#!/bin/bash

# Run a single split of all the specified psl-examples.

readonly THIS_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
readonly BASE_OUT_DIR="${THIS_DIR}/../results"

readonly ADDITIONAL_PSL_OPTIONS='-D log4j.threshold=DEBUG'

# An identifier to differentiate the output of this script/experiment from other scripts.
readonly RUN_ID='single-split'

function run_psl() {
    local cliDir=$1
    local outDir=$2
    local extraOptions=$3

    mkdir -p "${outDir}"

    local outPath="${outDir}/out.txt"
    local errPath="${outDir}/out.err"
    local timePath="${outDir}/time.txt"

    if [[ -e "${outPath}" ]]; then
        echo "Output file already exists, skipping: ${outPath}"
        return 0
    fi

    pushd . > /dev/null
        cd "${cliDir}"

        # Run PSL.
        /usr/bin/time -v --output="${timePath}" ./run.sh ${extraOptions} > "${outPath}" 2> "${errPath}"

        # Copy any artifacts into the output directory.
        cp -r inferred-predicates "${outDir}/"
        cp *.data "${outDir}/"
        cp *.psl "${outDir}/"
    popd > /dev/null
}

function run_example() {
    local exampleDir=$1

    local exampleName=`basename "${exampleDir}"`
    local cliDir="$exampleDir/cli"
    local outDir="${BASE_OUT_DIR}/${RUN_ID}/${exampleName}"
    local options="${ADDITIONAL_PSL_OPTIONS}"

    echo "Running ${exampleName} -- ${RUN_ID}."

    run_psl "${cliDir}" "${outDir}" "${options}"
}

function main() {
    if [[ $# -eq 0 ]]; then
        echo "USAGE: $0 <example dir> ..."
        exit 1
    fi

    trap exit SIGINT

    for exampleDir in "$@"; do
        run_example "${exampleDir}" "${i}"
    done
}

[[ "${BASH_SOURCE[0]}" == "${0}" ]] && main "$@"
