#!/usr/bin/env python3

import os

from pslpython.model import Model
from pslpython.partition import Partition
from pslpython.predicate import Predicate
from pslpython.rule import Rule

MODEL_NAME = 'social-network-analysis'
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
    predicate = Predicate('Bias', closed = True, size = 2)
    model.add_predicate(predicate)

    predicate = Predicate('Boss', closed = True, size = 2)
    model.add_predicate(predicate)

    predicate = Predicate('Idol', closed = True, size = 2)
    model.add_predicate(predicate)

    predicate = Predicate('Knows', closed = True, size = 2)
    model.add_predicate(predicate)

    predicate = Predicate('KnowsWell', closed = True, size = 2)
    model.add_predicate(predicate)

    predicate = Predicate('Mentor', closed = True, size = 2)
    model.add_predicate(predicate)

    predicate = Predicate('OlderRelative', closed = True, size = 2)
    model.add_predicate(predicate)

    predicate = Predicate('Votes', closed = False, size = 2)
    model.add_predicate(predicate)

def add_rules(model):
    model.add_rule(Rule("0.50: Bias(A, P) >> Votes(A, P) ^2"))
    model.add_rule(Rule("0.30: Votes(A, P) & KnowsWell(B, A) >> Votes(B, P) ^2"))
    model.add_rule(Rule("0.10: Votes(A, P) & Knows(B, A) >> Votes(B, P) ^2"))
    model.add_rule(Rule("0.05: Votes(A, P) & Boss(B, A) >> Votes(B, P) ^2"))
    model.add_rule(Rule("0.10: Votes(A, P) & Mentor(B, A) >> Votes(B, P) ^2"))
    model.add_rule(Rule("0.70: Votes(A, P) & OlderRelative(B, A) >> Votes(B, P) ^2"))
    model.add_rule(Rule("0.80: Votes(A, P) & Idol(B, A) >> Votes(B, P) ^2"))

    # Negative prior
    model.add_rule(Rule("0.01: !Votes(A, P) ^2"))

    # Partial functional on votes.
    model.add_rule(Rule("Votes(A, +B) <= 1 ."))

def add_data(model):
    for predicate in model.get_predicates().values():
        predicate.clear_data()

    path = os.path.join(DATA_DIR, 'bias_obs.txt')
    model.get_predicate('Bias').add_data_file(Partition.OBSERVATIONS, path)

    path = os.path.join(DATA_DIR, 'boss_obs.txt')
    model.get_predicate('Boss').add_data_file(Partition.OBSERVATIONS, path)

    path = os.path.join(DATA_DIR, 'idol_obs.txt')
    model.get_predicate('Idol').add_data_file(Partition.OBSERVATIONS, path)

    path = os.path.join(DATA_DIR, 'knows_obs.txt')
    model.get_predicate('Knows').add_data_file(Partition.OBSERVATIONS, path)

    path = os.path.join(DATA_DIR, 'knowswell_obs.txt')
    model.get_predicate('KnowsWell').add_data_file(Partition.OBSERVATIONS, path)

    path = os.path.join(DATA_DIR, 'mentor_obs.txt')
    model.get_predicate('Mentor').add_data_file(Partition.OBSERVATIONS, path)

    path = os.path.join(DATA_DIR, 'olderRelative_obs.txt')
    model.get_predicate('OlderRelative').add_data_file(Partition.OBSERVATIONS, path)

    path = os.path.join(DATA_DIR, 'votes_targets.txt')
    model.get_predicate('Votes').add_data_file(Partition.TARGETS, path)

def infer(model):
    add_data(model)
    return model.infer(additional_cli_optons = ADDITIONAL_CLI_OPTIONS, psl_config = ADDITIONAL_PSL_OPTIONS)

if (__name__ == '__main__'):
    main()
