#!/usr/bin/env python3

import os
import re

from pslpython.model import Model
from pslpython.partition import Partition
from pslpython.predicate import Predicate
from pslpython.rule import Rule

MODEL_NAME = 'knowledge-graph-identification'
DATA_DIR = os.path.join('..', 'data', 'kgi')

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
    predicates = [
        # (name, size, closed?)
        ('Sub', 2, True),
        ('RSub', 2, True),
        ('Mut', 2, True),
        ('RMut', 2, True),
        ('Inv', 2, True),
        ('Domain', 2, True),
        ('Range2', 2, True),
        ('SameEntity', 2, True),
        ('ValCat', 2, True),
        ('ValRel', 3, True),
        ('CandCat_General', 2, True),
        ('CandRel_General', 3, True),
        ('CandCat_CBL', 2, True),
        ('CandRel_CBL', 3, True),
        ('CandCat_CMC', 2, True),
        ('CandCat_CPL', 2, True),
        ('CandRel_CPL', 3, True),
        ('CandCat_Morph', 2, True),
        ('CandCat_SEAL', 2, True),
        ('CandRel_SEAL', 3, True),
        ('PromCat_General', 2, True),
        ('PromRel_General', 3, True),
        ('Cat', 2, False),
        ('Rel', 3, False),
    ]

    for (name, size, closed) in predicates:
        predicate = Predicate(name, closed = closed, size = size)
        model.add_predicate(predicate)

def add_rules(model):
    model.add_rule(Rule("025: VALCAT(B, C) & SAMEENTITY(A, B) & CAT(A, C) -> CAT(B, C) ^2"))
    model.add_rule(Rule("025: VALREL(B, Z, R) & SAMEENTITY(A, B) & REL(A, Z, R) -> REL(B, Z, R) ^2"))
    model.add_rule(Rule("025: VALREL(Z, B, R) & SAMEENTITY(A, B) & REL(Z, A, R) -> REL(Z, B, R) ^2"))
    model.add_rule(Rule("100: VALCAT(A, D) & SUB(C, D) & CAT(A, C) -> CAT(A, D) ^2"))
    model.add_rule(Rule("100: VALREL(A, B, S) & RSUB(R, S) & REL(A, B, R) -> REL(A, B, S) ^2"))
    model.add_rule(Rule("100: VALCAT(A, D) & MUT(C, D) & CAT(A, C) -> !CAT(A, D) ^2"))
    model.add_rule(Rule("100: VALREL(A, B, S) & RMUT(R, S) & REL(A, B, R) -> !REL(A, B, S) ^2"))
    model.add_rule(Rule("100: VALREL(B, A, S) & INV(R, S) & REL(A, B, R) -> REL(B, A, S) ^2"))
    model.add_rule(Rule("100: VALCAT(A, C) & DOMAIN(R, C) & REL(A, B, R) -> CAT(A, C) ^2"))
    model.add_rule(Rule("100: VALCAT(B, C) & RANGE2(R, C) & REL(A, B, R) -> CAT(B, C) ^2"))
    model.add_rule(Rule("001: VALCAT(A, C) & CANDCAT_GENERAL(A, C) -> CAT(A, C) ^2"))
    model.add_rule(Rule("001: VALREL(A, B, R) & CANDREL_GENERAL(A, B, R) -> REL(A, B, R) ^2"))
    model.add_rule(Rule("001: VALCAT(A, C) & CANDCAT_CBL(A, C) -> CAT(A, C) ^2"))
    model.add_rule(Rule("001: VALREL(A, B, R) & CANDREL_CBL(A, B, R) -> REL(A, B, R) ^2"))
    model.add_rule(Rule("001: VALCAT(A, C) & CANDCAT_CMC(A, C) -> CAT(A, C) ^2"))
    model.add_rule(Rule("001: VALCAT(A, C) & CANDCAT_CPL(A, C) -> CAT(A, C) ^2"))
    model.add_rule(Rule("001: VALREL(A, B, R) & CANDREL_CPL(A, B, R) -> REL(A, B, R) ^2"))
    model.add_rule(Rule("001: VALCAT(A, C) & CANDCAT_MORPH(A, C) -> CAT(A, C) ^2"))
    model.add_rule(Rule("001: VALCAT(A, C) & CANDCAT_SEAL(A, C) -> CAT(A, C) ^2"))
    model.add_rule(Rule("001: VALREL(A, B, R) & CANDREL_SEAL(A, B, R) -> REL(A, B, R) ^2"))
    model.add_rule(Rule("001: VALCAT(A, C) & PROMCAT_GENERAL(A, C) -> CAT(A, C) ^2"))
    model.add_rule(Rule("001: VALREL(A, B, R) & PROMREL_GENERAL(A, B, R) -> REL(A, B, R) ^2"))

    # Priors
    model.add_rule(Rule("002: VALCAT(A, C) -> !CAT(A, C) ^2"))
    model.add_rule(Rule("002: VALREL(A, B, R) -> !REL(A, B, R) ^2"))
    model.add_rule(Rule("001: VALCAT(A, C) -> CAT(A, C) ^2"))
    model.add_rule(Rule("001: VALREL(A, B, R) -> REL(A, B, R) ^2"))

def add_learn_data(model):
    _add_data('learn', model)

def add_eval_data(model):
    _add_data('eval', model)

def _add_data(split, model):
    split_data_dir = os.path.join(DATA_DIR, split)

    for predicate in model.get_predicates().values():
        predicate.clear_data()

    # The names of the files are very consistent and can therefore be scripted.

    # Match the partition identifier that the filename's use to the actual partitions.
    partition_map = {
        'obs': Partition.OBSERVATIONS,
        'targets': Partition.TARGETS,
        'truth': Partition.TRUTH,
    }

    for dirent in os.listdir(split_data_dir):
        path = os.path.join(split_data_dir, dirent)
        if (not os.path.isfile(path)):
            continue

        match = re.match(r'(\w+)_(obs|targets|truth)\.txt', dirent)
        if (match is None):
            raise ValueError("Data filename (%s) is malformed." % (path))

        predicate_name, partition = match.groups()
        model.get_predicate(predicate_name).add_data_file(partition_map[partition], path)

def learn(model):
    add_learn_data(model)
    model.learn(additional_cli_options = ADDITIONAL_CLI_OPTIONS, psl_config = ADDITIONAL_PSL_OPTIONS)

def infer(model):
    add_eval_data(model)
    return model.infer(additional_cli_options = ADDITIONAL_CLI_OPTIONS, psl_config = ADDITIONAL_PSL_OPTIONS)

if (__name__ == '__main__'):
    main()
