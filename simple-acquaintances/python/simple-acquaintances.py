#!/usr/bin/env python3

import os

from pslpython.model import Model
from pslpython.partition import Partition
from pslpython.predicate import Predicate
from pslpython.rule import Rule

MODEL_NAME = 'simple-acquaintances'
DATA_DIR = os.path.join('..', 'data')

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
    predicate = Predicate('Knows', closed = False, size = 2)
    model.add_predicate(predicate)

    predicate = Predicate('Likes', closed = True, size = 2)
    model.add_predicate(predicate)

    predicate = Predicate('Lived', closed = True, size = 2)
    model.add_predicate(predicate)

def add_rules(model):
    model.add_rule(Rule('20: Lived(P1, L) & Lived(P2, L) & (P1 != P2) -> Knows(P1, P2) ^2'))
    model.add_rule(Rule('5: Lived(P1, L1) & Lived(P2, L2) & (P1 != P2) & (L1 != L2) -> !Knows(P1, P2) ^2'))
    model.add_rule(Rule('10: Likes(P1, L) & Likes(P2, L) & (P1 != P2) -> Knows(P1, P2) ^2'))
    model.add_rule(Rule('5: Knows(P1, P2) & Knows(P2, P3) & (P1 != P3) -> Knows(P1, P3) ^2'))
    model.add_rule(Rule('Knows(P1, P2) = Knows(P2, P1) .'))
    model.add_rule(Rule('5: !Knows(P1, P2) ^2'))

def add_data(model):
    for predicate in model.get_predicates().values():
        predicate.clear_data()

    path = os.path.join(DATA_DIR, 'knows_obs.txt')
    model.get_predicate('Knows').add_data_file(Partition.OBSERVATIONS, path)

    path = os.path.join(DATA_DIR, 'lived_obs.txt')
    model.get_predicate('Lived').add_data_file(Partition.OBSERVATIONS, path)

    path = os.path.join(DATA_DIR, 'likes_obs.txt')
    model.get_predicate('Likes').add_data_file(Partition.OBSERVATIONS, path)

    path = os.path.join(DATA_DIR, 'knows_targets.txt')
    model.get_predicate('Knows').add_data_file(Partition.TARGETS, path)

    path = os.path.join(DATA_DIR, 'knows_truth.txt')
    model.get_predicate('Knows').add_data_file(Partition.TRUTH, path)

def infer(model):
    add_data(model)
    return model.infer(additional_cli_optons = ADDITIONAL_CLI_OPTIONS, psl_config = ADDITIONAL_PSL_OPTIONS)

if (__name__ == '__main__'):
    main()
