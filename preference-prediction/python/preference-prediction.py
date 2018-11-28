#!/usr/bin/env python3

import os

from pslpython.model import Model
from pslpython.partition import Partition
from pslpython.predicate import Predicate
from pslpython.rule import Rule

MODEL_NAME = 'preference-prediction'
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
    predicate = Predicate('AvgJokeRatingObs', closed = True, size = 1)
    model.add_predicate(predicate)

    predicate = Predicate('AvgUserRatingObs', closed = True, size = 1)
    model.add_predicate(predicate)

    predicate = Predicate('Joke', closed = True, size = 1)
    model.add_predicate(predicate)

    predicate = Predicate('RatingPrior', closed = True, size = 1)
    model.add_predicate(predicate)

    predicate = Predicate('SimObsRating', closed = True, size = 2)
    model.add_predicate(predicate)

    predicate = Predicate('User', closed = True, size = 1)
    model.add_predicate(predicate)

    predicate = Predicate('Rating', closed = False, size = 2)
    model.add_predicate(predicate)

def add_rules(model):
    # If J1,J2 have similar observed ratings, then U will rate them similarly
    model.add_rule(Rule("1.0: SimObsRating(J1,J2) & Rating(U,J1) >> Rating(U,J2) ^2"))

    # Ratings should concentrate around observed User/Joke averages
    model.add_rule(Rule("1.0: User(U) & Joke(J) & AvgUserRatingObs(U) >> Rating(U,J) ^2"))
    model.add_rule(Rule("1.0: User(U) & Joke(J) & AvgJokeRatingObs(J) >> Rating(U,J) ^2"))
    model.add_rule(Rule("1.0: User(U) & Joke(J) & Rating(U,J) >> AvgUserRatingObs(U) ^2"))
    model.add_rule(Rule("1.0: User(U) & Joke(J) & Rating(U,J) >> AvgJokeRatingObs(J) ^2"))

    # Two-sided prior
    model.add_rule(Rule("1.0: User(U) & Joke(J) & RatingPrior('0') >> Rating(U, J) ^2"))
    model.add_rule(Rule("1.0: Rating(U,J) >> RatingPrior('0') ^2"))

def add_learn_data(model):
    _add_data('learn', model)

def add_eval_data(model):
    _add_data('eval', model)

def _add_data(split, model):
    split_data_dir = os.path.join(DATA_DIR, split)

    for predicate in model.get_predicates().values():
        predicate.clear_data()

    path = os.path.join(split_data_dir, 'avgJokeRatingObs_obs.txt')
    model.get_predicate('AvgJokeRatingObs').add_data_file(Partition.OBSERVATIONS, path)

    path = os.path.join(split_data_dir, 'avgUserRatingObs_obs.txt')
    model.get_predicate('AvgUserRatingObs').add_data_file(Partition.OBSERVATIONS, path)

    path = os.path.join(split_data_dir, 'joke_obs.txt')
    model.get_predicate('Joke').add_data_file(Partition.OBSERVATIONS, path)

    path = os.path.join(split_data_dir, 'ratingPrior_obs.txt')
    model.get_predicate('RatingPrior').add_data_file(Partition.OBSERVATIONS, path)

    path = os.path.join(split_data_dir, 'simObsRating_obs.txt')
    model.get_predicate('SimObsRating').add_data_file(Partition.OBSERVATIONS, path)

    path = os.path.join(split_data_dir, 'user_obs.txt')
    model.get_predicate('User').add_data_file(Partition.OBSERVATIONS, path)

    path = os.path.join(split_data_dir, 'rating_obs.txt')
    model.get_predicate('Rating').add_data_file(Partition.OBSERVATIONS, path)

    path = os.path.join(split_data_dir, 'rating_targets.txt')
    model.get_predicate('Rating').add_data_file(Partition.TARGETS, path)

    path = os.path.join(split_data_dir, 'rating_truth.txt')
    model.get_predicate('Rating').add_data_file(Partition.TRUTH, path)

def learn(model):
    add_learn_data(model)
    model.learn(additional_cli_optons = ADDITIONAL_CLI_OPTIONS, psl_config = ADDITIONAL_PSL_OPTIONS)

def infer(model):
    add_eval_data(model)
    return model.infer(additional_cli_optons = ADDITIONAL_CLI_OPTIONS, psl_config = ADDITIONAL_PSL_OPTIONS)

if (__name__ == '__main__'):
    main()
