#!/bin/bash

set -e
trap exit SIGINT

# Run all run.sh scripts in psl-examples repo.
for line in `ls ./*/cli/run.sh` ; do
    dir=$(dirname $line)
    pushd . > /dev/null
        cd $dir
        ./run.sh --postgres psltest -D log4j.threshold=DEBUG
    popd > /dev/null
done
