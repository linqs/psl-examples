#!/usr/bin/env python3

import os

from pslpython.model import Model
from pslpython.partition import Partition
from pslpython.predicate import Predicate
from pslpython.rule import Rule

MODEL_NAME = 'citeseer'
DATA_DIR = os.path.join('..', 'data', MODEL_NAME, '0')

ADDITIONAL_PSL_OPTIONS = {
    'log4j.threshold': 'INFO'
}

ADDITIONAL_CLI_OPTIONS = [
    # '--postgres'
]

NUM_CATEGORIES = 7

def main():
    model = Model(MODEL_NAME)

    # Add Predicates

    link_predicate, hascat_predicate = add_predicates(model)

    # Add Rules

    add_rules(model)

    # Weight Learning

    learn(model, link_predicate, hascat_predicate)

    print('Learned Rules:')
    for rule in model.get_rules():
        print('   ' + str(rule))

    # Inference

    results = infer(model, link_predicate, hascat_predicate)

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
    link_predicate = Predicate('Link', closed = True, size = 2)
    model.add_predicate(link_predicate)

    hascat_predicate = Predicate('HasCat', closed = False, size = 2)
    model.add_predicate(hascat_predicate)

    return link_predicate, hascat_predicate

def add_rules(model):
    # Neighborhood rules.
    model.add_rule(Rule("1.0: HasCat(A, C) & Link(A, B) & (A != B) >> HasCat(B, C) ^2"))
    model.add_rule(Rule("1.0: HasCat(A, C) & Link(B, A) & (A != B) >> HasCat(B, C) ^2"))

    # Per category rules
    for i in range(NUM_CATEGORIES):
        # Categories are 1-indexed.
        rule_string = "HasCat(A, '%d') & Link(A, B) >> HasCat(B, '%d')" % (i + 1, i + 1)
        model.add_rule(Rule(rule_string, weight = 1.0, squared = True))

    # Ensure that HasCat sums to 1
    model.add_rule(Rule("HasCat(A, +C) = 1 ."))

    # Prior
    model.add_rule(Rule("0.001: !HasCat(A, N) ^2"))

def add_learn_data(link_predicate, hascat_predicate):
    _add_data('learn', link_predicate, hascat_predicate)

def add_eval_data(link_predicate, hascat_predicate):
    _add_data('eval', link_predicate, hascat_predicate)

def _add_data(split, link_predicate, hascat_predicate):
    split_data_dir = os.path.join(DATA_DIR, split)

    link_predicate.clear_data()
    hascat_predicate.clear_data()

    path = os.path.join(split_data_dir, 'hasCat_obs.txt')
    hascat_predicate.add_data_file(Partition.OBSERVATIONS, path)

    path = os.path.join(split_data_dir, 'link_obs.txt')
    link_predicate.add_data_file(Partition.OBSERVATIONS, path)

    path = os.path.join(split_data_dir, 'hasCat_targets.txt')
    hascat_predicate.add_data_file(Partition.TARGETS, path)

    path = os.path.join(split_data_dir, 'hasCat_truth.txt')
    hascat_predicate.add_data_file(Partition.TRUTH, path)

def learn(model, link_predicate, hascat_predicate):
    add_learn_data(link_predicate, hascat_predicate)
    model.learn(additional_cli_optons = ADDITIONAL_CLI_OPTIONS, psl_config = ADDITIONAL_PSL_OPTIONS)

def infer(model, link_predicate, hascat_predicate):
    add_eval_data(link_predicate, hascat_predicate)
    return model.infer(additional_cli_optons = ADDITIONAL_CLI_OPTIONS, psl_config = ADDITIONAL_PSL_OPTIONS)

if (__name__ == '__main__'):
    main()
