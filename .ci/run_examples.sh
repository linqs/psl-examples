#!/bin/bash

set -e
trap exit SIGINT

# Run all run.sh scripts in psl-examples repo.
for line in `ls ./*/cli/run.sh` ; do
    echo "Starting space"
    df -h
    du -csh *

    dir=$(dirname $line)
    pushd . > /dev/null
        cd $dir
        ./run.sh --postgres psltest -D log4j.threshold=DEBUG -D votedperceptron.numsteps=2 -D admmreasoner.maxiterations=10
    popd > /dev/null

    echo "Ending space"
    df -h
    du -csh *
done
