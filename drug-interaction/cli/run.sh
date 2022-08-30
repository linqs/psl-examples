#!/bin/bash

# Options can also be passed on the command line.
# These options are passed blindly to the PSL CLI.
# Ex: ./run.sh -D log4j.threshold=DEBUG

readonly THIS_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

readonly PSL_VERSION='3.0.0-SNAPSHOT'
readonly JAR_PATH="${THIS_DIR}/psl-cli-${PSL_VERSION}.jar"
readonly RUN_SCRIPT_VERSION='1.3.6'

readonly BASE_NAME='drug-interaction'
readonly OUTPUT_DIRECTORY="${THIS_DIR}/inferred-predicates"

readonly ADDITIONAL_PSL_OPTIONS=" \
    --int-ids \
    --eval AUCEvaluator \
"

readonly ADDITIONAL_EVAL_OPTIONS="--infer \
    --eval DiscreteEvaluator \
    -D discreteevaluator.threshold=0.4 \
"

readonly ADDITIONAL_WL_OPTIONS="--learn GaussianProcessPrior \
    -D weightlearning.evaluator=AUCEvaluator \
"

function main() {
    trap exit SIGINT

    # TODO: fetch it later
    # bash "${THIS_DIR}/../data/fetchData.sh"

    # Make sure we can run PSL.
    check_requirements
    fetch_psl

    # Run PSL.
    run_weight_learning "$@"
    run_inference "$@"
}

function run_weight_learning() {
    echo "Running PSL Weight Learning."

    java -jar "${JAR_PATH}" \
        --model "${THIS_DIR}/${BASE_NAME}.psl" \
        --data "${THIS_DIR}/${BASE_NAME}-learn.data" \
        ${ADDITIONAL_PSL_OPTIONS} ${ADDITIONAL_WL_OPTIONS} "$@"

    if [[ "$?" -ne 0 ]]; then
        echo 'ERROR: Failed to run weight learning.'
        exit 60
    fi
}

function run_inference() {
    echo "Running PSL Inference."

    java -jar "${JAR_PATH}" \
        --model "${THIS_DIR}/${BASE_NAME}-learned.psl" \
        --data "${THIS_DIR}/${BASE_NAME}-eval.data" \
        --output "${OUTPUT_DIRECTORY}" \
        ${ADDITIONAL_PSL_OPTIONS} ${ADDITIONAL_EVAL_OPTIONS} "$@"

    if [[ "$?" -ne 0 ]]; then
        echo 'ERROR: Failed to run infernce.'
        exit 70
    fi
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

    type java > /dev/null 2> /dev/null
    if [[ "$?" -ne 0 ]]; then
        echo 'ERROR: java required to run project.'
        exit 13
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

# Fetch the jar from a remote or local location and put it in this directory.
# Non-snapshot builds are fetched from Maven Central.
# For snapshot builds, the local maven cache ($HOME/.m2) is checked first, and then the snapshot deployment servers.
# Snapshots are fetched from the local maven repo and other builds are fetched remotely.
function fetch_psl() {
    if [[ -e "${JAR_PATH}" ]] ; then
        echo "Using PSL jar found at ${JAR_PATH}. To fetch a new version, delete this cached jar."
        return
    fi

    if [[ "${PSL_VERSION}" =~ .*-SNAPSHOT$ ]]; then
        local snapshotLocalPath="$HOME/.m2/repository/org/linqs/psl-cli/${PSL_VERSION}/psl-cli-${PSL_VERSION}.jar"
        if [[ -e "${snapshotLocalPath}" ]] ; then
            echo "Using local PSL snapshot build."
            cp "${snapshotLocalPath}" "${JAR_PATH}"
            return
        fi

        echo "Using remote PSL snapshot build."
        local snotshotMetadataURL="https://oss.sonatype.org/content/repositories/snapshots/org/linqs/psl-cli/${PSL_VERSION}/maven-metadata.xml"
        local metadataFilename='._maven-metadata.xml'

        rm -f "${metadataFilename}"
        fetch_file "${snotshotMetadataURL}" "${metadataFilename}"

        local snapshotDate=$(grep -m 1 'timestamp' "${metadataFilename}" | sed -E 's/^.*>([0-9]+\.[0-9]+)<.*$/\1/')
        local snapshotNumber=$(grep -m 1 'buildNumber' "${metadataFilename}" | sed -E 's/^.*>([0-9]+)<.*$/\1/')
        rm -f "${metadataFilename}"

        local baseVersion=$(echo "${PSL_VERSION}" | sed -E 's/-SNAPSHOT$//')
        local version="${baseVersion}-${snapshotDate}-${snapshotNumber}"

        local snotshotJarURL="https://oss.sonatype.org/content/repositories/snapshots/org/linqs/psl-cli/${PSL_VERSION}/psl-cli-${version}.jar"
        fetch_file "${snotshotJarURL}" "${JAR_PATH}"
    else
        echo "Using remote PSL build."
        local remoteJarURL="https://repo1.maven.org/maven2/org/linqs/psl-cli/${PSL_VERSION}/psl-cli-${PSL_VERSION}.jar"
        fetch_file "${remoteJarURL}" "${JAR_PATH}"
    fi
}

[[ "${BASH_SOURCE[0]}" == "${0}" ]] && main "$@"
