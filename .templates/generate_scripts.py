#!/usr/bin/env python3

import json
import os
import stat
import sys

THIS_DIR = os.path.join(os.path.dirname(os.path.realpath(__file__)))
ROOT_DIR = os.path.join(THIS_DIR, '..')

CLI_INFERENCE_TEMPLATE_PATH = os.path.join(THIS_DIR, 'cli_run_template_inference.sh')
CLI_WEIGHT_LEARNING_TEMPLATE_PATH = os.path.join(THIS_DIR, 'cli_run_template_weight_learning.sh')

EXAMPLES_CONFIG_PATH = os.path.join(THIS_DIR, 'config.json')

SCRIPT_VERSION = '1.3.2'
PSL_VERSION = '2.3.0-SNAPSHOT'

TEMPLATE_SUBS = {
    '__SCRIPT_VERSION__': SCRIPT_VERSION,
    '__PSL_VERSION__': PSL_VERSION,
    '__FETCH_DATA__': 'bash "${THIS_DIR}/../data/fetchData.sh"',

    # Subs still needing values.
    '__BASE_NAME__': None,
    '__PSL_OPTIONS__': None,
    '__EVAL_OPTIONS__': None,
    '__WL_OPTIONS__': None,
}

def generateCLIScript(baseName,
        weightLearning = True, fetchData = True,
        pslOptions = '', evalOptions = '', weightLearningOptions = ''):
    template_path = CLI_INFERENCE_TEMPLATE_PATH
    if (weightLearning):
        template_path = CLI_WEIGHT_LEARNING_TEMPLATE_PATH

    subs = dict(TEMPLATE_SUBS)
    subs['__BASE_NAME__'] = baseName

    if (not weightLearning):
        subs['__PSL_OPTIONS__'] = '--infer ' + pslOptions
        del subs['__EVAL_OPTIONS__']
        del subs['__WL_OPTIONS__']
    else:
        subs['__PSL_OPTIONS__'] = pslOptions
        subs['__EVAL_OPTIONS__'] = '--infer ' + evalOptions
        subs['__WL_OPTIONS__'] = '--learn ' + weightLearningOptions

    if (not fetchData):
        subs['__FETCH_DATA__'] = '# No data fetching necessary.'

    with open(template_path, 'r') as file:
        contents = file.read()

    for (find, replace) in subs.items():
        contents = contents.replace(find, replace.strip())

    return contents

def main():
    with open(EXAMPLES_CONFIG_PATH, 'r') as file:
        config = json.load(file)

    for baseName in config:
        scriptContents = generateCLIScript(baseName, **config[baseName])

        outPath = os.path.join(ROOT_DIR, baseName, 'cli', 'run.sh')
        with open(outPath, 'w') as file:
            file.write(scriptContents)

        os.chmod(outPath, 0o775)

def _load_args(args):
    executable = args.pop(0)
    if (len(args) != 0 or ({'h', 'help'} & {arg.lower().strip().replace('-', '') for arg in args})):
        print("USAGE: python3 %s" % (executable, OPERATION_OVERALL, OPERATION_CURVE), file = sys.stderr)
        sys.exit(1)

if (__name__ == '__main__'):
    _load_args(sys.argv)
    main()
