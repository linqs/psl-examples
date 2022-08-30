#!/bin/bash

# Runs 10 fold cross validation for a dataset specified in the first argument. 
# Example: ./run_folds.sh general-interactions

function run_psl() {
    local outDir=$1
    local extraOptions=$2

    mkdir -p "${outDir}"

    local outPath="${outDir}/out.txt"
    local errPath="${outDir}/out.err"

    if [[ -e "${outPath}" ]]; then
        echo "Output file already exists, skipping: ${outPath}"
        return 0
    fi

    # Run PSL.
    ./cli/run.sh ${extraOptions} > "${outPath}" 2> "${errPath}"

    # Copy any artifacts into the output directory.
    cp -r cli/inferred-predicates "${outDir}/"
    cp cli/*.data "${outDir}/"
    cp cli/*.psl "${outDir}/"
}


function main() {
    trap exit SIGINT

    local experiment=$1

    local outDir=""
    local extraOptions="-D runtime.log.level=DEBUG"

    # Initialize the dataset
    sed -ri "s|data/[^/]*/|data/${experiment}/|" cli/drug-interaction*.data

    # Initialize folds in the data files to start at 0
    sed -ri "s|([0-9]+)(/eval)|0\2|" cli/drug-interaction*.data


    # FIXME: use su seq -w 00 09
    # for i in $(seq -w 00 09)

    # Run 10 fold CV
    for i in {0..9}
    do
      echo "Inferring Fold $i"
      sed -ri "s|([0-9]+)(/eval)|${i}\2|" cli/drug-interaction*.data

      outDir="results/experiment::${experiment}/fold::${i}"

      run_psl "$outDir" "$extraOptions"

    done

    # restore original data file to default dataset general-interactions. 
    sed -ri "s|data/[^/]*/|data/general-interactions/|" cli/drug-interaction*.data

    # restore original data file to start at the 0th fold
    sed -ri "s|([0-9]+)(/eval)|0\2|" cli/drug-interaction*.data
}

[[ "${BASH_SOURCE[0]}" == "${0}" ]] && main "$@"
