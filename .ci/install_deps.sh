#!/bin/bash

# Install any dependencies we need.

readonly THIS_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
readonly GENERATE_SCRIPT="${THIS_DIR}/../.templates/generate_scripts.py"

function main() {
    if [[ $# -ne 0 ]] ; then
        echo "USAGE: $0"
        exit 1
    fi

    trap exit SIGINT
    set -e

    local pslVersion=$(grep '^PSL_VERSION' "${GENERATE_SCRIPT}" | sed "s/^.* = '\(.\+\)'$/\1/")
    local versionNumber=$(echo "${pslVersion}" | sed 's/-SNAPSHOT//')

    if [[ ${pslVersion} =~ -SNAPSHOT$ ]] ; then
        python3 -m pip install --user --upgrade --index-url https://test.pypi.org/simple/ --extra-index-url https://pypi.org/simple/ "pslpython>=${versionNumber}.dev,<${versionNumber}.rc"
    else
        python3 -m pip install --user --upgrade pslpython
    fi
}

[[ "${BASH_SOURCE[0]}" == "${0}" ]] && main "$@"
