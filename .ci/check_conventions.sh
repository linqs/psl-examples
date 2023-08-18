#!/bin/bash

# Ensure that examples are following proper conventions.

readonly THIS_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
readonly BASE_DIR="${THIS_DIR}/.."
readonly TEMPLATE_CONFIG_PATH="${BASE_DIR}/.templates/config.json"
readonly GENERATION_SCRIPT="${BASE_DIR}/.templates/generate_scripts.py"

# Check that every example has a specific path.
function check_path() {
    local relativePath=$1
    local skipExamples=" $2 "

    local returnValue=0

    for exampleDir in "${BASE_DIR}/"* ; do
        local exampleName=$(basename "${exampleDir}")

        if [[ ! -d "${exampleDir}" || "${exampleName}" == '_scripts' || "${skipExamples}" == *" ${exampleName} "* ]] ; then
            continue
        fi

        local targetPath="${exampleDir}/${relativePath}"
        if [[ ! -f "${targetPath}" ]] ; then
            echo "ERROR: Cannot find path: ${targetPath}."
            returnValue=1
        fi
    done

    return ${returnValue}
}

# Check that every example is present in the config (and visa-versa).
function check_config() {
    local returnValue=0
    local count=0

    for exampleDir in "${BASE_DIR}/"* ; do
        local exampleName=$(basename "${exampleDir}")

        if [[ ! -d "${exampleDir}" || "${exampleName}" == '_scripts' ]] ; then
            continue
        fi

        let count++

        # Ensure that this example appears in the config.
        grep "\"${exampleName}\": {" "${TEMPLATE_CONFIG_PATH}" > /dev/null
        if [[ ! $? ]] ; then
            echo "ERROR: Example (${exampleName}) not found in the template config."
            returnValue=1
        fi
    done

    # Ensure that we have seen the same number of examples represented in the config.
    local configCount=$(grep '^    "' "${TEMPLATE_CONFIG_PATH}" | wc -l | sed 's/ //g')
    if [[ ${count} != ${configCount} ]] ; then
        echo "ERROR: Number of examples found in config (${configCount}) does not match number of seen config dirs (${count})."
        returnValue=1
    fi

    return ${returnValue}
}

# Use git commands to ensure that all generated files match the currently committed files.
function check_generation() {
    local changeCount=$(git diff --name-only | wc -l | sed 's/ //g')
    if [[ ${changeCount} != 0 ]] ; then
        echo "ERROR: Changes in git seen even before generation, should be running in a clean checkout."
        return 1
    fi

    "${GENERATION_SCRIPT}"

    changeCount=$(git diff --name-only | wc -l | sed 's/ //g')
    if [[ ${changeCount} != 0 ]] ; then
        echo "ERROR: Changes seen in git after script generation:"
        git diff --name-only
        return 1
    fi

    return 0
}

function main() {
    if [[ $# -ne 0 ]] ; then
        echo "USAGE: $0"
        exit 1
    fi

    trap exit SIGINT

    echo "Checking conventions ..."

    local status=0

    check_path 'cli/run.sh'
    status=$(($status | $?))

    check_path 'data/.gitignore' 'simple-acquaintances smokers'
    status=$(($status | $?))

    check_path 'README.md'
    status=$(($status | $?))

    check_config
    status=$(($status | $?))

    check_generation
    status=$(($status | $?))

    if [[ $status -eq 0 ]]; then
        echo "All convention checks passed!"
    else
        echo "Some convention checks have failed, see output for details."
    fi

    return $status
}

[[ "${BASH_SOURCE[0]}" == "${0}" ]] && main "$@"
