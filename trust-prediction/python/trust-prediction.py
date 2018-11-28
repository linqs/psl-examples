#!/usr/bin/env python3

import os

from pslpython.model import Model
from pslpython.partition import Partition
from pslpython.predicate import Predicate
from pslpython.rule import Rule

MODEL_NAME = 'trust-prediction'
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

    # Weight Learning
    learn(model)

    print('Learned Rules:')
    for rule in model.get_rules():
        print('   ' + str(rule))

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
    predicate = Predicate('Trusts', closed = False, size = 2)
    model.add_predicate(predicate)

    predicate = Predicate('Knows', closed = True, size = 2)
    model.add_predicate(predicate)

    predicate = Predicate('Prior', closed = True, size = 1)
    model.add_predicate(predicate)

def add_rules(model):
    # FFpp
    model.add_rule(Rule("1.0: Knows(A, B) & Knows(B, C) & Knows(A, C) & Trusts(A, B) & Trusts(B, C) & (A != B) & (B != C) & (A != C) -> Trusts(A, C) ^2"))
    # FFpm
    model.add_rule(Rule("1.0: Knows(A, B) & Knows(B, C) & Knows(A, C) & Trusts(A, B) & !Trusts(B, C) & (A != B) & (B != C) & (A != C) -> !Trusts(A, C) ^2"))
    # FFmp
    model.add_rule(Rule("1.0: Knows(A, B) & Knows(B, C) & Knows(A, C) & !Trusts(A, B) & Trusts(B, C) & (A != B) & (B != C) & (A != C) -> !Trusts(A, C) ^2"))
    # FFmm
    model.add_rule(Rule("1.0: Knows(A, B) & Knows(B, C) & Knows(A, C) & !Trusts(A, B) & !Trusts(B, C) & (A != B) & (B != C) & (A != C) -> Trusts(A, C) ^2"))

    # FBpp
    model.add_rule(Rule("1.0: Knows(A, B) & Knows(C, B) & Knows(A, C) & Trusts(A, B) & Trusts(C, B) & (A != B) & (B != C) & (A != C) -> Trusts(A, C) ^2"))
    # FBpm
    model.add_rule(Rule("1.0: Knows(A, B) & Knows(C, B) & Knows(A, C) & Trusts(A, B) & !Trusts(C, B) & (A != B) & (B != C) & (A != C) -> !Trusts(A, C) ^2"))
    # FBmp
    model.add_rule(Rule("1.0: Knows(A, B) & Knows(C, B) & Knows(A, C) & !Trusts(A, B) & Trusts(C, B) & (A != B) & (B != C) & (A != C) -> !Trusts(A, C) ^2"))
    # FBmm
    model.add_rule(Rule("1.0: Knows(A, B) & Knows(C, B) & Knows(A, C) & !Trusts(A, B) & !Trusts(C, B) & (A != B) & (B != C) & (A != C) -> Trusts(A, C) ^2"))

    # BFpp
    model.add_rule(Rule("1.0: Knows(B, A) & Knows(B, C) & Knows(A, C) & Trusts(B, A) & Trusts(B, C) & (A != B) & (B != C) & (A != C) -> Trusts(A, C) ^2"))
    # BFpm
    model.add_rule(Rule("1.0: Knows(B, A) & Knows(B, C) & Knows(A, C) & Trusts(B, A) & !Trusts(B, C) & (A != B) & (B != C) & (A != C) -> !Trusts(A, C) ^2"))
    # BFmp
    model.add_rule(Rule("1.0: Knows(B, A) & Knows(B, C) & Knows(A, C) & !Trusts(B, A) & Trusts(B, C) & (A != B) & (B != C) & (A != C) -> !Trusts(A, C) ^2"))
    # BFmm
    model.add_rule(Rule("1.0: Knows(B, A) & Knows(B, C) & Knows(A, C) & !Trusts(B, A) & !Trusts(B, C) & (A != B) & (B != C) & (A != C) -> Trusts(A, C) ^2"))

    # BBpp
    model.add_rule(Rule("1.0: Knows(B, A) & Knows(C, B) & Knows(A, C) & Trusts(B, A) & Trusts(C, B) & (A != B) & (B != C) & (A != C) -> Trusts(A, C) ^2"))
    # BBpm
    model.add_rule(Rule("1.0: Knows(B, A) & Knows(C, B) & Knows(A, C) & Trusts(B, A) & !Trusts(C, B) & (A != B) & (B != C) & (A != C) -> !Trusts(A, C) ^2"))
    # BBmp
    model.add_rule(Rule("1.0: Knows(B, A) & Knows(C, B) & Knows(A, C) & !Trusts(B, A) & Trusts(C, B) & (A != B) & (B != C) & (A != C) -> !Trusts(A, C) ^2"))
    # BBmm
    model.add_rule(Rule("1.0: Knows(B, A) & Knows(C, B) & Knows(A, C) & !Trusts(B, A) & !Trusts(C, B) & (A != B) & (B != C) & (A != C) -> Trusts(A, C) ^2"))

    model.add_rule(Rule("1.0: Knows(A, B) & Knows(B, A) & Trusts(A, B) -> Trusts(B, A) ^2"))
    model.add_rule(Rule("1.0: Knows(A, B) & Knows(B, A) & !Trusts(A, B) -> !Trusts(B, A) ^2"))

    # two-sided prior
    model.add_rule(Rule("1.0: Knows(A, B) & Prior('0') -> Trusts(A, B) ^2"))
    model.add_rule(Rule("1.0: Knows(A, B) & Trusts(A, B) -> Prior('0') ^2"))

def add_learn_data(model):
    _add_data('learn', model)

def add_eval_data(model):
    _add_data('eval', model)

def _add_data(split, model):
    split_data_dir = os.path.join(DATA_DIR, split)

    for predicate in model.get_predicates().values():
        predicate.clear_data()

    path = os.path.join(split_data_dir, 'trusts_obs.txt')
    model.get_predicate('Trusts').add_data_file(Partition.OBSERVATIONS, path)

    path = os.path.join(split_data_dir, 'knows_obs.txt')
    model.get_predicate('Knows').add_data_file(Partition.OBSERVATIONS, path)

    path = os.path.join(split_data_dir, 'prior_obs.txt')
    model.get_predicate('Prior').add_data_file(Partition.OBSERVATIONS, path)

    path = os.path.join(split_data_dir, 'trusts_target.txt')
    model.get_predicate('Trusts').add_data_file(Partition.TARGETS, path)

    path = os.path.join(split_data_dir, 'trusts_truth.txt')
    model.get_predicate('Trusts').add_data_file(Partition.TRUTH, path)

def learn(model):
    add_learn_data(model)
    model.learn(additional_cli_optons = ADDITIONAL_CLI_OPTIONS, psl_config = ADDITIONAL_PSL_OPTIONS)

def infer(model):
    add_eval_data(model)
    return model.infer(additional_cli_optons = ADDITIONAL_CLI_OPTIONS, psl_config = ADDITIONAL_PSL_OPTIONS)

if (__name__ == '__main__'):
    main()
