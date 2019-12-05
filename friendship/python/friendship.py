#!/usr/bin/env python3

import os

from pslpython.model import Model
from pslpython.partition import Partition
from pslpython.predicate import Predicate
from pslpython.rule import Rule

MODEL_NAME = 'friendship'
DATA_DIR = os.path.join('..', 'data', MODEL_NAME)

ADDITIONAL_PSL_OPTIONS = {
    'log4j.threshold': 'INFO'
}

ADDITIONAL_CLI_OPTIONS = [
    # '--postgres'
]

def main():
    model = Model(MODEL_NAME)

    # Add Predicates
    add_predicates(model)

    # Add Rules
    add_rules(model)

    # Inference
    results = infer(model)

    write_results(results, model)

def write_results(results, model):
    out_dir = 'inferred-predicates'
    os.makedirs(out_dir, exist_ok = True)

    for predicate in model.get_predicates().values():
        if (predicate.closed()):
            continue

        out_path = os.path.join(out_dir, "%s.txt" % (predicate.name()))
        results[predicate].to_csv(out_path, sep = "\t", header = False, index = False)

def add_predicates(model):
    predicate = Predicate('Similar', closed = True, size = 2)
    model.add_predicate(predicate)

    predicate = Predicate('Block', closed = True, size = 2)
    model.add_predicate(predicate)

    predicate = Predicate('Friends', closed = False, size = 2)
    model.add_predicate(predicate)

def add_rules(model):
    model.add_rule(Rule("10: Block(P1, A) & Block(P2, A) & Similar(P1, P2) & P1 != P2 -> Friends(P1, P2) ^2"))
    model.add_rule(Rule("10: Block(P1, A) & Block(P2, A) & Block(P3, A) & Friends(P1, P2) & Friends(P2, P3) & P1 != P2 & P2 != P3 & P1 != P3 -> Friends(P1, P3) ^2"))
    model.add_rule(Rule("10: Block(P1, A) & Block(P2, A) & Friends(P1, P2) & P1 != P2 -> Friends(P2, P1) ^2"))

    # Negative prior.
    model.add_rule(Rule("1: !Friends(P1, P2) ^2"))

def add_data(model):
    for predicate in model.get_predicates().values():
        predicate.clear_data()

    path = os.path.join(DATA_DIR, 'location_obs.txt')
    model.get_predicate('Block').add_data_file(Partition.OBSERVATIONS, path)

    path = os.path.join(DATA_DIR, 'similar_obs.txt')
    model.get_predicate('Similar').add_data_file(Partition.OBSERVATIONS, path)

    path = os.path.join(DATA_DIR, 'friends_targets.txt')
    model.get_predicate('Friends').add_data_file(Partition.TARGETS, path)

    path = os.path.join(DATA_DIR, 'friends_truth.txt')
    model.get_predicate('Friends').add_data_file(Partition.TRUTH, path)

def infer(model):
    add_data(model)
    return model.infer(additional_cli_optons = ADDITIONAL_CLI_OPTIONS, psl_config = ADDITIONAL_PSL_OPTIONS)

if (__name__ == '__main__'):
    main()
