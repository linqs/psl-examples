#!/bin/bash

readonly THIS_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

readonly DATA_URL='https://linqs-data.soe.ucsc.edu/public/psl-examples-data/entity-resolution/entity-resolution-small.zip'
readonly DATA_FILE=$(basename "${DATA_URL}")
readonly DATA_DIR='entity-resolution'
readonly SCRIPT_VERSION='1.3.3'

function main() {
    trap exit SIGINT

    cd "${THIS_DIR}"

    check_requirements

    fetch_file "${DATA_URL}" "${DATA_FILE}"
    extract_zip "${DATA_FILE}" "${DATA_DIR}"
}

function check_requirements() {
    local hasWget
    local hasCurl

    type wget > /dev/null 2> /dev/null
    hasWget=$?

    type curl > /dev/null 2> /dev/null
    hasCurl=$?

    if [[ "${hasWget}" -ne 0 ]] && [[ "${hasCurl}" -ne 0 ]]; then
        echo 'ERROR: wget or curl required to download the jar.'
        exit 10
    fi
}

function get_fetch_command() {
    type curl > /dev/null 2> /dev/null
    if [[ "$?" -eq 0 ]]; then
        echo "curl -o"
        return
    fi

    type wget > /dev/null 2> /dev/null
    if [[ "$?" -eq 0 ]]; then
        echo "wget -O"
        return
    fi

    echo 'ERROR: wget or curl not found.'
    exit 20
}

function fetch_file() {
    local url=$1
    local path=$2

    local name=$(basename "${path}")

    if [[ -e "${path}" ]]; then
        echo "${name} file found cached, skipping download."
        return
    fi

    echo "Downloading ${name} file located at: '${url}'."
    `get_fetch_command` "${path}" "${url}"
    if [[ "$?" -ne 0 ]]; then
        echo "ERROR: Failed to download ${name}."
        exit 30
    fi
}


function extract_zip() {
    local path=$1
    local expectedDir=$2

    local name=$(basename "${path}")

    if [[ -e "${expectedDir}" ]]; then
        echo "Extracted ${name} zip found cached, skipping extract."
        return
    fi

    echo "Extracting the ${name} zip"
    unzip "${path}"
    if [[ "$?" -ne 0 ]]; then
        echo "ERROR: Failed to extract ${name}."
        exit 40
    fi
}

[[ "${BASH_SOURCE[0]}" == "${0}" ]] && main "$@"
