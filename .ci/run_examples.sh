#!/bin/bash

# run all run.sh scripts in psl-examples repo
ls ./*/cli/run.sh | while read line; do
    dir=$(dirname $line)
    pushd . > /dev/null
        cd $dir;
        ./run.sh --postgres psltest -D log4j.threshold=DEBUG
    popd > /dev/null
done