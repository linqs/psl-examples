#!/bin/bash

set -e
trap exit SIGINT

# Build and install the PSL core matching this version.
BUILD_DIR='/tmp/__building_psl_core__'
TARGET_REPOS="$@"

if [ $# -eq 0 ]; then
   echo "No target repositories specified."
   exit 1
fi

# If the pushed branch is master, we will use master for core.
# Otherwise, we will use develop.
branch='develop'
if [ "$TRAVIS_BRANCH" = 'master' ]; then
   branch='master'
fi

# We will use the same owner for PSL core as this repo's owner.
owner=$(echo "$TRAVIS_REPO_SLUG" | sed 's#/.\+$##')

pushd . > /dev/null
    for repo in $TARGET_REPOS; do
        gitUrl="https://github.com/${owner}/${repo}.git"
        echo "Building ${gitUrl} (${branch}) ..."

        cd
        rm -Rf "${BUILD_DIR}"
        mkdir -p "${BUILD_DIR}"
        cd "${BUILD_DIR}"

        git clone "${gitUrl}"
        cd "${repo}"
        git checkout "${branch}"

        mvn clean install -DskipTests
    done
popd > /dev/null
