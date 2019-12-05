#!/usr/bin/env python3

import os

from pslpython.model import Model
from pslpython.partition import Partition
from pslpython.predicate import Predicate
from pslpython.rule import Rule

MODEL_NAME = 'user-modeling'
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
    predicate = Predicate('Predicts', closed = True, size = 4)
    model.add_predicate(predicate)

    predicate = Predicate('Friend', closed = True, size = 2)
    model.add_predicate(predicate)

    predicate = Predicate('Likes', closed = True, size = 2)
    model.add_predicate(predicate)

    predicate = Predicate('Joins', closed = True, size = 2)
    model.add_predicate(predicate)

    predicate = Predicate('Has', closed = True, size = 2)
    model.add_predicate(predicate)

    predicate = Predicate('Is', closed = False, size = 3)
    model.add_predicate(predicate)

def add_rules(model):
    # Priors from local classifiers
    model.add_rule(Rule("50: Has(U, S) & Predicts(S, U, A, L) -> Is(U, A, L) ^2"))
    model.add_rule(Rule("50: Has(U, S) & ~Predicts(S, U, A, L) -> ~Is(U, A, L) ^2"))

    # Collective Rules for relational signals
    model.add_rule(Rule("100: Joins(U, G) & Joins(V, G) & Is(V, A, L) -> Is(U, A, L) ^2"))
    model.add_rule(Rule("100: Joins(U, G) & Joins(V, G) & ~Is(V, A, L) -> ~Is(U, A, L) ^2"))
    model.add_rule(Rule("10: Likes(U, T) & Likes(V, T) & Is(V, A, L) -> Is(U, A, L) ^2"))
    model.add_rule(Rule("10: Likes(U, T) & Likes(V, T) & ~Is(V, A, L) -> ~Is(U, A, L) ^2"))

    model.add_rule(Rule("1: Friend(U, V) & Is(V, A, L)-> Is(U, A, L) ^2"))
    model.add_rule(Rule("1: Friend(U, V) & ~Is(V, A, L)-> ~Is(U, A, L) ^2"))
    model.add_rule(Rule("1: Friend(V, U) & Is(V, A, L)-> Is(U, A, L) ^2"))
    model.add_rule(Rule("1: Friend(V, U) & ~Is(V, A, L)-> ~Is(U, A, L) ^2"))

    # Ensure that user has one attribute
    model.add_rule(Rule("1: Is(U, A, +L) = 1"))

def add_data(model):
    for predicate in model.get_predicates().values():
        predicate.clear_data()

    path = os.path.join(DATA_DIR, 'local_predictor_obs.txt')
    model.get_predicate('Predicts').add_data_file(Partition.OBSERVATIONS, path)

    path = os.path.join(DATA_DIR, 'has_obs.txt')
    model.get_predicate('Has').add_data_file(Partition.OBSERVATIONS, path)

    path = os.path.join(DATA_DIR, 'friend_obs.txt')
    model.get_predicate('Friend').add_data_file(Partition.OBSERVATIONS, path)

    path = os.path.join(DATA_DIR, 'likes_obs.txt')
    model.get_predicate('Likes').add_data_file(Partition.OBSERVATIONS, path)

    path = os.path.join(DATA_DIR, 'joins_obs.txt')
    model.get_predicate('Joins').add_data_file(Partition.OBSERVATIONS, path)

    path = os.path.join(DATA_DIR, 'user_train.txt')
    model.get_predicate('Is').add_data_file(Partition.OBSERVATIONS, path)

    path = os.path.join(DATA_DIR, 'user_target.txt')
    model.get_predicate('Is').add_data_file(Partition.TARGETS, path)

    path = os.path.join(DATA_DIR, 'user_truth.txt')
    model.get_predicate('Is').add_data_file(Partition.TRUTH, path)

def infer(model):
    add_data(model)
    return model.infer(additional_cli_optons = ADDITIONAL_CLI_OPTIONS, psl_config = ADDITIONAL_PSL_OPTIONS)

if (__name__ == '__main__'):
    main()
