#!/usr/bin/env python3

import os

from pslpython.model import Model
from pslpython.partition import Partition
from pslpython.predicate import Predicate
from pslpython.rule import Rule

MODEL_NAME = 'entity-resolution'
SPLIT = '0'
DATA_DIR = os.path.join('..', 'data', MODEL_NAME, SPLIT)

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
    predicate = Predicate('AuthorName', closed = True, arg_types = [Predicate.ArgType.UNIQUE_INT_ID, Predicate.ArgType.UNIQUE_STRING_ID])
    model.add_predicate(predicate)

    predicate = Predicate('AuthorBlock', closed = True, arg_types = [Predicate.ArgType.UNIQUE_INT_ID, Predicate.ArgType.UNIQUE_STRING_ID])
    model.add_predicate(predicate)

    predicate = Predicate('PaperTitle', closed = True, arg_types = [Predicate.ArgType.UNIQUE_INT_ID, Predicate.ArgType.UNIQUE_STRING_ID])
    model.add_predicate(predicate)

    predicate = Predicate('AuthorOf', closed = True, arg_types = [Predicate.ArgType.UNIQUE_INT_ID, Predicate.ArgType.UNIQUE_INT_ID])
    model.add_predicate(predicate)

    predicate = Predicate('SimName', closed = True, arg_types = [Predicate.ArgType.UNIQUE_STRING_ID, Predicate.ArgType.UNIQUE_STRING_ID])
    model.add_predicate(predicate)

    predicate = Predicate('SimTitle', closed = True, arg_types = [Predicate.ArgType.UNIQUE_STRING_ID, Predicate.ArgType.UNIQUE_STRING_ID])
    model.add_predicate(predicate)

    predicate = Predicate('SameAuthor', closed = False, arg_types = [Predicate.ArgType.UNIQUE_INT_ID, Predicate.ArgType.UNIQUE_INT_ID])
    model.add_predicate(predicate)

    predicate = Predicate('SamePaper', closed = False, arg_types = [Predicate.ArgType.UNIQUE_INT_ID, Predicate.ArgType.UNIQUE_INT_ID])
    model.add_predicate(predicate)

def add_rules(model):
    # Look for text similarity.
    model.add_rule(Rule("40.0: AuthorName(A1, N1) & AuthorName(A2, N2) & SimName(N1, N2) & (A1 != A2) -> SameAuthor(A1, A2) ^2"))
    model.add_rule(Rule("40.0: PaperTitle(P1, T1) & PaperTitle(P2, T2) & SimTitle(T1, T2) & (P1 != P2) -> SamePaper(P1, P2) ^2"))

    # Pure transitivity
    model.add_rule(Rule("""
        20.0: AuthorBlock(A1, B) & AuthorBlock(A2, B) & AuthorBlock(A3, B)
            & SameAuthor(A1, A2) & SameAuthor(A2, A3)
            & (A1 != A3) & (A1 != A2) & (A2 != A3)
            -> SameAuthor(A1, A3) ^2
    """))

    # Coauthor rectangle closure.
    model.add_rule(Rule("""
        20.0: AuthorBlock(A1, B1) & AuthorBlock(A2, B1) & AuthorBlock(CA1, B2) & AuthorBlock(CA2, B2) &
            & AuthorOf(A1, P1) & AuthorOf(A2, P2)
            & AuthorOf(CA1, P1) & AuthorOf(CA2, P2) & SameAuthor(CA1, CA2)
            & (A1 != CA1) & (A2 != CA2) & (P1 != P2)
            -> SameAuthor(A1, A2) ^2
    """))

    # Paper rectangle closure.
    model.add_rule(Rule("""
        10.0: AuthorBlock(A1, B1) & AuthorBlock(A2, B1)
            & AuthorOf(A1, P1) & AuthorOf(A2, P2)
            & SamePaper(P1, P2)
            -> SameAuthor(A1, A2) ^2
    """))

    # Self-refernece.
    model.add_rule(Rule("SameAuthor(A, A) = 1.0 ."))
    model.add_rule(Rule("SamePaper(P, P) = 1.0 ."))

    # Negative priors.
    model.add_rule(Rule("1.0: !SameAuthor(A1, A2) ^2"))
    model.add_rule(Rule("1.0: !SamePaper(A1, A2) ^2"))

def add_learn_data(model):
    _add_data('learn', model)

def add_eval_data(model):
    _add_data('eval', model)

def _add_data(split, model):
    split_data_dir = os.path.join(DATA_DIR, split)

    for predicate in model.get_predicates().values():
        predicate.clear_data()

    path = os.path.join(split_data_dir, 'authorName_obs.txt')
    model.get_predicate('AuthorName').add_data_file(Partition.OBSERVATIONS, path)

    path = os.path.join(split_data_dir, 'authorBlock_obs.txt')
    model.get_predicate('AuthorBlock').add_data_file(Partition.OBSERVATIONS, path)

    path = os.path.join(split_data_dir, 'paperTitle_obs.txt')
    model.get_predicate('PaperTitle').add_data_file(Partition.OBSERVATIONS, path)

    path = os.path.join(split_data_dir, 'authorOf_obs.txt')
    model.get_predicate('AuthorOf').add_data_file(Partition.OBSERVATIONS, path)

    path = os.path.join(split_data_dir, 'simName_obs.txt')
    model.get_predicate('SimName').add_data_file(Partition.OBSERVATIONS, path)

    path = os.path.join(split_data_dir, 'simTitle_obs.txt')
    model.get_predicate('SimTitle').add_data_file(Partition.OBSERVATIONS, path)

    path = os.path.join(split_data_dir, 'sameAuthor_targets.txt')
    model.get_predicate('SameAuthor').add_data_file(Partition.TARGETS, path)

    path = os.path.join(split_data_dir, 'samePaper_targets.txt')
    model.get_predicate('SamePaper').add_data_file(Partition.TARGETS, path)

    path = os.path.join(split_data_dir, 'sameAuthor_truth.txt')
    model.get_predicate('SameAuthor').add_data_file(Partition.TRUTH, path)

    path = os.path.join(split_data_dir, 'samePaper_truth.txt')
    model.get_predicate('SamePaper').add_data_file(Partition.TRUTH, path)

def learn(model):
    add_learn_data(model)
    model.learn(additional_cli_optons = ADDITIONAL_CLI_OPTIONS, psl_config = ADDITIONAL_PSL_OPTIONS)

def infer(model):
    add_eval_data(model)
    return model.infer(additional_cli_optons = ADDITIONAL_CLI_OPTIONS, psl_config = ADDITIONAL_PSL_OPTIONS)

if (__name__ == '__main__'):
    main()
