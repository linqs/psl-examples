"""
Generates data for the simple-acquaintances PSL example.

Use -h/--help for usage options.
"""

import argparse
import json
import numpy as np
import os

from itertools import combinations
from scipy.stats import truncnorm
from sklearn.metrics.pairwise import cosine_similarity

DEFAULT_GLOBAL_LIKES = 5
DEFAULT_LOCAL_LIKES = 5
DEFAULT_LOCAL_LIKES_VARIANCE = 1.0
DEFAULT_PEOPLE = 25
DEFAULT_PLACES = 5
DEFAULT_PLACES_LIVED_MEAN = 3.0
DEFAULT_PLACES_LIVED_SD = 1.0
DEFAULT_TARGET_SPLIT = 0.2


def checkNonNegativeInt(arg):
    """
    Check if argument is non negative integer.
    """
    if not arg.isdigit():
        raise argparse.ArgumentTypeError(
            '{} is not a non negative integer.'.format(arg))
    return int(arg)


def checkValidSplit(arg):
    """
    Check if argument is a valid split for targets i.e {0...1}.
    """
    try:
        if 0 <= float(arg) <= 1:
            return arg
        else:
            raise argparse.ArgumentTypeError('The input should be a valid float between [0, 1].')
    except ValueError as e:
        raise argparse.ArgumentTypeError('{}. The input should be a valid float between [0, 1].'
                                        .format(e))


def checkNonNegativeFloat(arg):
    """
    Check if argument is non negative float.
    """
    try:
        if 0 <= float(arg):
            return arg
        else:
            raise argparse.ArgumentTypeError('{} is not a non negative float.'.format(arg))
    except ValueError as e:
        raise argparse.ArgumentTypeError('{}. The input should be a non negative float value.'
                                        .format(e))


def parse_args():
    """
    Parse CLI arguments.
    """
    parser = argparse.ArgumentParser(
        description='Generate data for the PSL simple-acquaintances example.')

    parser.add_argument(
        '--coinFlipSeed',
        default = None,
        type = float,
        help = 'Seed for the random coin flip function.')

    parser.add_argument(
        '--globalLikes',
        default = DEFAULT_GLOBAL_LIKES,
        type = checkNonNegativeInt,
        help = 'Number of global things to like.')

    parser.add_argument(
        '--localLikes',
        default = DEFAULT_LOCAL_LIKES,
        type = checkNonNegativeInt,
        help = 'Number of local things to like.')

    parser.add_argument(
        '--localLikesVariance',
        default = DEFAULT_LOCAL_LIKES_VARIANCE,
        type = checkNonNegativeFloat,
        help = 'Variance for likeability of a local thing based on place lived.')

    parser.add_argument(
        '--outputDir',
        default  = None,
        type = str,
        help = 'Output directory where all data files will be written to.')

    parser.add_argument(
        '--people',
        default = DEFAULT_PEOPLE,
        type = checkNonNegativeInt,
        help = 'Number of people in your dataset.')

    parser.add_argument(
        '--places',
        default = DEFAULT_PLACES,
        type = checkNonNegativeInt,
        help = 'Number of places in your dataset.')

    parser.add_argument(
        '--placesLivedMean',
        default = DEFAULT_PLACES_LIVED_MEAN,
        type = checkNonNegativeInt,
        help = 'Mean of the number of places a person has lived in.')

    parser.add_argument(
        '--placesLivedSD',
        default = DEFAULT_PLACES_LIVED_SD,
        type = checkNonNegativeFloat,
        help = 'Standard deviation of the number of places a person has lived in.')

    parser.add_argument(
        '--targetSplit',
        default = DEFAULT_TARGET_SPLIT,
        type = checkValidSplit,
        help = 'Fraction of the truth data to be used as targets.')

    args = parser.parse_args()
    return args


def getTruncatedNormal(mean, sd, low, upp):
    """
    Return truncated normal distribution.
    """
    return truncnorm(
        (low - mean) / sd, (upp - mean) / sd, loc = mean, scale = sd)


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

    def __init__(
            self,
            numberOfPeople,
            numberOfPlaces,
            numberOfGlobal,
            numberOfLocal,
            placesLivedMean,
            placesLivedSD,
            localLikesVariance,
            targetSplit,
            coinFlipSeed,
            outputDir):
        self.numberOfPeople = numberOfPeople
        self.places = [i for i in range(numberOfPlaces)]
        self.globalThings = [i for i in range(numberOfGlobal)]
        self.localThings = [i for i in range(numberOfLocal)]
        self.placesLivedMean = placesLivedMean
        self.placesLivedSD = placesLivedSD
        self.placesLivedUpper = numberOfPlaces
        self.localLikesVariance = localLikesVariance
        self.targetSplit = targetSplit
        self.coinFlipSeed = coinFlipSeed
        self.outputDir = outputDir

        self.placesAffectingLocalThings = {}
        self.__generatePALT()  # Fill in placesAffectingLocalThings.

    def __generatePALT(self):
        """
        Generate porbabilities of places affecting the likeablity of local things.

        eg: [Place1:[LThing1, Lthing2], Place2:[LThing1,LThing2]]
        """
        for i in self.places:
            self.placesAffectingLocalThings[i] = np.random.uniform(
                0, 1, len(self.localThings))

    def __getLikeability(self, person):
        """
        Return likeability of localThings based on the places that a person has lived in.
        """
        likeability = {}
        for thing in self.localThings:
            likeability[thing] = 0
            for place in person.lived:
                truncnorm_gen = getTruncatedNormal(self.placesAffectingLocalThings[place][thing],
                                                    self.localLikesVariance, 0, 1)
                likeability[thing] += truncnorm_gen.rvs()
            likeability[thing] /= len(person.lived)
        
        return likeability

    def __flipCoin(self, seed, bias = 0.5):
        """
        Return 1 or 0 based on a biased coin toss. Default bias = 0.5.
        """
        if seed:
            np.random.seed(seed)

        return 1 if np.random.random() < bias else 0

    def __calculateSimilarity(self, p1, p2):
        """
        Return similarity measure between two persons (cosine similarity [0.0...1.0]).
        """
        person1 = np.array(p1.lived 
                            + list(p1.globalLikes.values()) 
                            + list(p1.localLikes.values())).reshape(1, -1)
        person2 = np.array(p2.lived 
                            + list(p2.globalLikes.values()) 
                            + list(p2.localLikes.values())).reshape(1, -1)

        return cosine_similarity(person1, person2)

    def __generatePeople(self):
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
            placesLived = np.random.choice(self.places, numberOfPlacesLived, replace = False)
            # One hot encoding of places lived by a person.
            person.lived = [1 if i in placesLived else 0 for i in range(len(self.places))]

            person.globalLikes = dict(zip(self.globalThings, 
                                            np.random.uniform(0, 1, len(self.globalThings))))

            person.localLikes = self.__getLikeability(person)
            
            people.append(person)

        return people

    def __generateKnows(self, people):
        """
        Return the knows truth matrix for all people in the dataset.
        """
        knowsMatrix = np.zeros((self.numberOfPeople, self.numberOfPeople))
        for pair in combinations(people, 2):
            similarity = self.__calculateSimilarity(pair[0], pair[1])
            knows = self.__flipCoin(self.coinFlipSeed, similarity)

            # Set both (i,j) and (j,i) since P1 knows P2 => P2 knows P1.
            knowsMatrix[pair[0].index][pair[1].index] = knows
            knowsMatrix[pair[1].index][pair[0].index] = knows

        return knowsMatrix

    def __writeDataToFile(self, data, paths):
        """
        Write data to file.
        """
        if self.outputDir:
            if self.outputDir[0] == '~':
                prefix = os.path.expanduser(self.outputDir)
            else:
                prefix = self.outputDir
        else:
            prefix = ''

        for key in data:
            # We have the same keys for data and its corresponding path.
            with open(os.path.join(prefix, paths[key]), 'w') as f:
                if key == 'config':
                    json.dump(data[key], f, indent = 4, sort_keys = True)
                else:
                    for dataItem in data[key]:
                        f.write("\t".join([str(d) for d in dataItem]) + "\n")

    def __prepareDataToWrite(self, knowsMatrix, people):
        """
        Prepare data for writing to file.
        """
        n = knowsMatrix.shape[1]

        knowsData = []
        knowsObs = []
        knowsTarget = []
        knowsTruth = []
        
        totalCombinations = knowsMatrix.shape[0] * (knowsMatrix.shape[1] - 1)
        targets = np.random.choice(range(totalCombinations), 
                                    int(self.targetSplit * totalCombinations), replace = False)
        for i in range(knowsMatrix.shape[0]):
            for j in range(knowsMatrix.shape[1]):
                if i != j:
                    knowsData.append([i, j, knowsMatrix[i][j]])
                if (n * i + j) not in targets and i != j:
                    knowsObs.append([i, j, knowsMatrix[i][j]])
                elif (n * i + j) in targets and i != j:
                    knowsTarget.append([i, j])
                if (n * i + j) in targets and i != j:
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

        # NOTE: Both global and local likes are combined in a single list called likesObs.
        for person in people:
            for localLike in person.localLikes:
                likesObs.append([person.index, len(self.globalThings) + localLike, person.localLikes[localLike]])
        
        config = {  'People': self.numberOfPeople,
                    'Places': len(self.places),
                    'Global things': len(self.globalThings),
                    'Local things': len(self.localThings),
                    'Places lived mean': self.placesLivedMean,
                    'Places lived standard deviation': self.placesLivedSD,
                    'Local likes variance': self.localLikesVariance,
                    'Target split': self.targetSplit,
                    'Coin flip seed': self.coinFlipSeed,
                    'Output directory': self.outputDir}

        data = {
            'knowsData': knowsData,
            'knowsObs': knowsObs,
            'knowsTarget': knowsTarget,
            'knowsTruth': knowsTruth,
            'livedObs': livedObs,
            'likesObs': likesObs,
            'config': config}
        return data

    def generateData(self):
        """
        Driver function to generate all truth data.
        """
        # 1. Generate people.
        people = self.__generatePeople()
        # 2. Generate  Knows truth data.
        knowsMatrix = self.__generateKnows(people)
        # 3. Write data to files.
        data = self.__prepareDataToWrite(knowsMatrix, people)
        paths = {
            'knowsData': 'knows_data.txt',
            'knowsObs': 'knows_obs.txt',
            'knowsTarget': 'knows_targets.txt',
            'knowsTruth': 'knows_truth.txt',
            'livedObs': 'lived_obs.txt',
            'likesObs': 'likes_obs.txt',
            'config': 'options.json'}
        self.__writeDataToFile(data, paths)




if __name__ == "__main__":
    args = parse_args()
    datagen = DataGen(
        args.people,
        args.places,
        args.globalLikes,
        args.localLikes,
        args.placesLivedMean,
        args.placesLivedSD,
        args.localLikesVariance,
        args.targetSplit,
        args.coinFlipSeed,
        args.outputDir)
    datagen.generateData()
