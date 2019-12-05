#!/usr/bin/env python3

"""
Generates data for the simple-acquaintances PSL example.

Use -h/--help for usage options.
"""

import argparse
import itertools
import json
import os

import numpy
import scipy
from sklearn.metrics.pairwise import cosine_similarity

DEFAULT_GLOBAL_LIKES = 5
DEFAULT_LOCAL_LIKES = 5
DEFAULT_LOCAL_LIKES_VARIANCE = 1.0
DEFAULT_MAX_RAND_INT = 2**32
DEFAULT_PEOPLE = 25
DEFAULT_PLACES = 5
DEFAULT_PLACES_LIVED_MEAN = 3.0
DEFAULT_PLACES_LIVED_SD = 1.0
DEFAULT_TARGET_RATIO = 0.2

def nonNegativeInt(arg):
    """
    Check if argument is a non negative integer.
    .isdigit() checks if all the characters in a string are digits.
    This works because it does not accept floats or negative integers.
    """

    if not arg.isdigit():
        raise ValueError(
            '{} is not a non negative integer.'.format(arg))
    return int(arg)

def validRatio(arg):
    """
    Check if argument is a valid ratio for targets i.e {0...1}.
    """

    try:
        if 0.0 <= float(arg) and float(arg) <= 1.0:
            return float(arg)
        else:
            raise ValueError('Invalid input {}. The input should be a valid float between [0, 1].'
                .format(arg))
    except ValueError as ex:
        raise ValueError('{}. The input should be a valid float between [0, 1].'
                .format(ex))

def nonNegativeFloat(arg):
    """
    Check if argument is non negative float.
    """

    try:
        if 0 <= float(arg):
            return float(arg)
        else:
            raise ValueError('{} is not a non negative float.'.format(arg))
    except ValueError as e:
        raise ValueError('{}. The input should be a non negative float value.'
                .format(e))

def parseArgs():
    """
    Parse CLI arguments.
    """

    parser = argparse.ArgumentParser(
        description='Generate data for the PSL simple-acquaintances example.')

    parser.add_argument(
        '--globalLikes',
        default = DEFAULT_GLOBAL_LIKES,
        type = nonNegativeInt,
        help = 'Number of global things to like.')

    parser.add_argument(
        '--localLikes',
        default = DEFAULT_LOCAL_LIKES,
        type = nonNegativeInt,
        help = 'Number of local things to like.')

    parser.add_argument(
        '--localLikesVariance',
        default = DEFAULT_LOCAL_LIKES_VARIANCE,
        type = nonNegativeFloat,
        help = 'Variance for likeability of a local thing based on place lived.')

    parser.add_argument(
        '--outputDir',
        default = './data',
        type = str,
        help = 'Output directory to which all data files will be written.')

    parser.add_argument(
        '--people',
        default = DEFAULT_PEOPLE,
        type = nonNegativeInt,
        help = 'Number of people in your dataset.')

    parser.add_argument(
        '--places',
        default = DEFAULT_PLACES,
        type = nonNegativeInt,
        help = 'Number of places in your dataset.')

    parser.add_argument(
        '--placesLivedMean',
        default = DEFAULT_PLACES_LIVED_MEAN,
        type = nonNegativeInt,
        help = 'Mean of the number of places a person has lived in.')

    parser.add_argument(
        '--placesLivedSD',
        default = DEFAULT_PLACES_LIVED_SD,
        type = nonNegativeFloat,
        help = 'Standard deviation of the number of places a person has lived in.')

    parser.add_argument(
        '--seed',
        default = numpy.random.randint(DEFAULT_MAX_RAND_INT),
        type = int,
        help = 'Seed for the random number generator used in the script.')

    parser.add_argument(
        '--targetRatio',
        default = DEFAULT_TARGET_RATIO,
        type = validRatio,
        help = 'Fraction of the truth data to be used as targets.')

    return parser.parse_args()

def getTruncatedNormal(mean, sd, lower, upper):
    """
    Return truncated normal distribution.
    """

    return scipy.stats.truncnorm(
        (lower - mean) / sd, (upper - mean) / sd, loc = mean, scale = sd)

class Person():
    """
    Person class for the dataset.
    """

    def __init__(self):
        self.index = None
        self.globalLikes = None
        self.lived = None
        self.localLikes = None

class DataGen():
    """
    Data generator class for simple-acquaintances example.
    """

    def __init__(self, numberOfPeople, numberOfPlaces, numberOfGlobal, numberOfLocal,
            placesLivedMean, placesLivedSD, localLikesVariance, targetRatio,
            seed, outputDir):
        self.globalThings = [i for i in range(numberOfGlobal)]
        self.localLikesVariance = localLikesVariance
        self.localThings = [i for i in range(numberOfLocal)]
        self.numberOfPeople = numberOfPeople
        self.outputDir = outputDir
        self.places = [i for i in range(numberOfPlaces)]
        self.placesLivedMean = placesLivedMean
        self.placesLivedSD = placesLivedSD
        self.placesLivedUpper = numberOfPlaces
        self.seed = seed
        self.rng = self._createRNG(seed)
        self.targetRatio = targetRatio

        self.placesAffectingLocalThings = {}
        self._generatePALT()  # Fill in placesAffectingLocalThings.

    def _createRNG(self, seed):
        """
        Return an instance of a random number generator for this class.
        """

        return numpy.random.RandomState(seed)

    def _generatePALT(self):
        """
        Generate porbabilities of places affecting the likeablity of local things.

        eg: [Place1:[LThing1, Lthing2], Place2:[LThing1,LThing2]]
        """

        for i in self.places:
            self.placesAffectingLocalThings[i] = self.rng.uniform(0, 1, len(self.localThings))

    def _getLikeability(self, person):
        """
        Return likeability of localThings based on the places that a person has lived in.
        """

        likeability = {}
        for thing in self.localThings:
            likeability[thing] = 0
            for place in person.lived:
                truncnorm_gen = getTruncatedNormal(self.placesAffectingLocalThings[place][thing], self.localLikesVariance, 0, 1)
                likeability[thing] += truncnorm_gen.rvs()
            likeability[thing] /= len(person.lived)

        return likeability

    def _flipCoin(self, bias = 0.5):
        """
        Return 1 or 0 based on a biased coin toss. Default bias = 0.5.
        """

        if self.rng.random_sample() < bias:
            return 1

        return  0

    def _calculateSimilarity(self, p1, p2):
        """
        Return similarity measure between two persons (cosine similarity [0.0...1.0]).
        """

        person1 = numpy.array(p1.lived
                + list(p1.globalLikes.values())
                + list(p1.localLikes.values())).reshape(1, -1)
        person2 = numpy.array(p2.lived
                + list(p2.globalLikes.values())
                + list(p2.localLikes.values())).reshape(1, -1)

        return cosine_similarity(person1, person2)

    def _generatePeople(self):
        """
        Generate list of people with their feature vectors filled in.
        """

        people = []
        for i in range(self.numberOfPeople):
            person = Person()

            person.index = i

            truncnormGenerator = getTruncatedNormal(self.placesLivedMean, self.placesLivedSD,
                    1, self.placesLivedUpper)
            numberOfPlacesLived = int(truncnormGenerator.rvs())
            person.lived = [0] * len(self.places)
            placesLived = self.rng.choice(self.places, numberOfPlacesLived, replace = False)
            # One hot encoding of places lived by a person.
            person.lived = [1 if i in placesLived else 0 for i in range(len(self.places))]

            person.globalLikes = dict(zip(self.globalThings,
                    self.rng.uniform(0, 1, len(self.globalThings))))

            person.localLikes = self._getLikeability(person)

            people.append(person)

        return people

    def _generateKnows(self, people):
        """
        Return the knows truth matrix for all people in the dataset.
        """

        knowsMatrix = numpy.zeros((self.numberOfPeople, self.numberOfPeople))
        for pair in itertools.combinations(people, 2):
            similarity = self._calculateSimilarity(pair[0], pair[1])
            knows = self._flipCoin(similarity)

            # Set both (i,j) and (j,i) since P1 knows P2 => P2 knows P1.
            knowsMatrix[pair[0].index][pair[1].index] = knows
            knowsMatrix[pair[1].index][pair[0].index] = knows

        return knowsMatrix

    def _writeDataToFile(self, data, paths):
        """
        Write data to file.
        """

        os.makedirs(self.outputDir, exist_ok=True)
        for key in data:
            # We have the same keys for data and its corresponding path.
            with open(os.path.join(self.outputDir, paths[key]), 'w') as file:
                if key == 'config':
                    json.dump(data[key], file, indent = 4, sort_keys = True)
                else:
                    for dataItem in data[key]:
                        file.write("\t".join([str(d) for d in dataItem]) + "\n")

    def _prepareDataToWrite(self, knowsMatrix, people):
        """
        Prepare data for writing to file.
        """

        n = knowsMatrix.shape[1]

        knowsData = []
        knowsObs = []
        knowsTarget = []
        knowsTruth = []

        totalCombinations = knowsMatrix.shape[0] * (knowsMatrix.shape[1] - 1)
        targets = self.rng.choice(range(totalCombinations),
                int(self.targetRatio * totalCombinations), replace = False)

        for i in range(knowsMatrix.shape[0]):
            for j in range(knowsMatrix.shape[1]):
                if i == j:
                    continue

                knowsData.append([i, j, knowsMatrix[i][j]])
                if (n * i + j) not in targets:
                    knowsObs.append([i, j, knowsMatrix[i][j]])
                elif (n * i + j) in targets:
                    knowsTarget.append([i, j])

                if (n * i + j) in targets:
                    knowsTruth.append([i, j, knowsMatrix[i][j]])

        livedObs = []
        for person in people:
            for index, place in enumerate(person.lived):
                if place == 1:
                    livedObs.append([person.index, index])

        likesObs = []
        for person in people:
            for globalLike in person.globalLikes:
                likesObs.append([person.index, globalLike, person.globalLikes[globalLike]])

        # Both global and local likes are combined in a single list called likesObs.
        for person in people:
            for localLike in person.localLikes:
                likesObs.append([person.index, len(self.globalThings) + localLike, person.localLikes[localLike]])

        config = {
            'people': self.numberOfPeople,
            'places': len(self.places),
            'globalThings': len(self.globalThings),
            'localThings': len(self.localThings),
            'placesLivedMean': self.placesLivedMean,
            'placesLivedStandardDeviation': self.placesLivedSD,
            'localLikesVariance': self.localLikesVariance,
            'targetRatio': self.targetRatio,
            'seed': self.seed,
        }

        return {
            'knowsData': knowsData,
            'knowsObs': knowsObs,
            'knowsTarget': knowsTarget,
            'knowsTruth': knowsTruth,
            'livedObs': livedObs,
            'likesObs': likesObs,
            'config': config
        }

    def generateData(self):
        """
        Driver function to generate all truth data.
        """

        # 1. Generate people.
        people = self._generatePeople()
        # 2. Generate Knows truth data.
        knowsMatrix = self._generateKnows(people)
        # 3. Write data to files.
        data = self._prepareDataToWrite(knowsMatrix, people)
        paths = {
            'knowsData': 'knows_data.txt',
            'knowsObs': 'knows_obs.txt',
            'knowsTarget': 'knows_targets.txt',
            'knowsTruth': 'knows_truth.txt',
            'livedObs': 'lived_obs.txt',
            'likesObs': 'likes_obs.txt',
            'config': 'options.json'}
        self._writeDataToFile(data, paths)

if __name__ == "__main__":
    args = parseArgs()

    # Resolve path if necessary before creating an instance of DataGen.
    dirToWrite = os.path.realpath(os.path.expanduser(args.outputDir))
    datagen = DataGen(
        args.people,
        args.places,
        args.globalLikes,
        args.localLikes,
        args.placesLivedMean,
        args.placesLivedSD,
        args.localLikesVariance,
        args.targetRatio,
        args.seed,
        dirToWrite)
    datagen.generateData()
