"""
This script generates data for the simple-acquaintances PSL example.

Additional parameters can be passed as optional arguments via the CLI.
"""

import argparse
import numpy as np

from itertools import combinations
from scipy.stats import truncnorm
from sklearn.metrics.pairwise import cosine_similarity


def checkPositiveInt(arg):
    """Check if argument is positive integer."""
    if not arg.isdigit():
        raise argparse.ArgumentTypeError(
            '{} is not a non negative integer.'.format(arg))
    return int(arg)


def parse_args():
    """Parse CLI arguments."""
    parser = argparse.ArgumentParser(
        description='Generate data for the PSL simple-acquaintances example.')
    parser.add_argument('--people', default=25, type=checkPositiveInt,
                        help='Number of people in your dataset.')
    parser.add_argument(
        '--places',
        default=5,
        type=checkPositiveInt,
        help='Number of places in your dataset.')
    parser.add_argument(
        '--placesLivedMean',
        default=3,
        type=checkPositiveInt,
        help='Mean of the number of places a person has lived in.')
    parser.add_argument(
        '--placesLivedSD',
        default=2,
        type=checkPositiveInt,
        help='Standard deviation of the number of places a person has lived in.')
    parser.add_argument('--globalLikes', default=5, type=checkPositiveInt,
                        help='Number of global things to like.')

    parser.add_argument(
        '--localLikes',
        default=5,
        type=checkPositiveInt,
        help='Number of local things to like.')
    parser.add_argument(
        '--localLikesVariance',
        default=1,
        type=checkPositiveInt,
        help='Variance for likability of a local thing based on place lived.')
    args = parser.parse_args()
    return args


def get_truncated_normal(mean=0, sd=1, low=0, upp=10):
    """Return truncated normal distribution."""
    return truncnorm(
        (low - mean) / sd, (upp - mean) / sd, loc=mean, scale=sd)


class Person():
    """Person class for the dataset."""

    def __init__(self):
        self.index = None
        self.lived = None
        self.globalLikes = None
        self.localLikes = None


class DataGen():
    """Generate data for simple-acquaintances example."""

    def __init__(
            self,
            noOfPeople,
            noOfPlaces,
            noOfGlobal,
            noOfLocal,
            placesLivedMean,
            placesLivedSD,
            localLikesVariance):
        self.noOfPeople = noOfPeople
        self.places = np.arange(noOfPlaces)
        self.globalThings = np.arange(noOfGlobal)
        self.localThings = np.arange(noOfLocal)

        self.placesAffectingLocalThings = {}
        self.__generatePALT()  # fill in placesAffectingLocalThings

        self.placesLivedMean = placesLivedMean
        self.placesLivedSD = placesLivedSD
        self.placesLivedUpper = noOfPlaces
        self.localLikesVariance = localLikesVariance
        # self.truncnormGenerator = get_truncated_normal(mean, sd, 1, upper)

    def __generatePALT(self):
        """Generate porbabilities of places affecting the likable-ity of local things."""
        # eg: [Place1:[LThing1, Lthing2], Place2:[LThing1,LThing2]]

        noOfLocalThings = self.localThings.size
        for i in self.places:
            self.placesAffectingLocalThings[i] = np.random.uniform(
                0, 1, noOfLocalThings)

    def __getLikability(self, person):
        """Return likeability for a localThing based on the places that the person has lived in."""
        likeability = {}
        for thing in self.localThings:
            likeability[thing] = 0
            for place in person.lived[0]:
                truncnorm_gen = get_truncated_normal(
                    self.placesAffectingLocalThings[place][thing], self.localLikesVariance, 0, 1)
                likeability[thing] += truncnorm_gen.rvs()
            likeability[thing] /= person.lived.size
        return likeability

    def __flipCoin(self, bias=0.5):
        """Return 1 or 0 based on a biased coin toss. Default bias = 0.5."""
        return 1 if np.random.random() < bias else 0

    def __calculateSimilarity(self, p1, p2):
        """Return similarity measure between two persons (cosine similarity [0-1])."""
        person1 = p1.lived
        person1 = np.concatenate(
            (person1, np.array(list(p1.globalLikes.values()))), axis=None)
        person1 = np.concatenate(
            (person1, np.array(list(p1.localLikes.values()))), axis=None)
        person1 = person1.reshape((1, -1))

        person2 = p2.lived
        person2 = np.concatenate(
            (person2, np.array(list(p2.globalLikes.values()))), axis=None)
        person2 = np.concatenate(
            (person2, np.array(list(p2.localLikes.values()))), axis=None)
        person2 = person2.reshape((1, -1))

        return cosine_similarity(person1, person2)

    def __generatePeople(self):
        """Generate list of people with their feature matrices filled in."""
        people = []
        for i in range(self.noOfPeople):
            person = Person()

            person.index = i
            truncnormGenerator = get_truncated_normal(
                self.placesLivedMean, self.placesLivedSD, 1, self.placesLivedUpper)
            noOfPlacesLived = int(truncnormGenerator.rvs())

            person.lived = np.zeros(self.places.size).reshape(1, -1)
            person.lived[0][np.random.choice(
                self.places, noOfPlacesLived, replace=False).tolist()] = 1

            person.globalLikes = dict(
                zip(self.globalThings, np.random.uniform(0, 1, self.globalThings.size)))
            person.localLikes = self.__getLikability(person)
            people.append(person)
        return people

    def __generateKnows(self, people):
        """Return the knows truth matrix for all people in the dataset."""
        knowsMatrix = np.zeros((self.noOfPeople, self.noOfPeople))
        for pair in combinations(people, 2):
            similarity = self.__calculateSimilarity(pair[0], pair[1])
            knows = self.__flipCoin(similarity)

            # set both (i,j) and (j,i) since P1 knows P2 => P2 knows P1.
            knowsMatrix[pair[0].index][pair[1].index] = knows
            knowsMatrix[pair[1].index][pair[0].index] = knows

        return knowsMatrix

    def __writeDataToFile(self, data, paths):
        """Write data to file."""
        # We have the same keys for data and its corresponding path.
        for key in data:
            with open(paths[key], 'w') as f:
                for dataItem in data[key]:
                    f.write(dataItem)

    def __prepareDataToWrite(self, knowsMatrix, people):
        """Prepare data to write to file."""
        n = knowsMatrix.shape[1]

        knowsData = []
        knowsObs = []
        knowsTarget = []
        knowsTruth = []

        targets = np.random.choice(np.arange(625), 200, replace=False)
        for i in range(knowsMatrix.shape[0]):
            for j in range(knowsMatrix.shape[1]):
                if i != j:
                    knowsData.append('\t'.join(
                        [str(i), str(j), str(knowsMatrix[i][j])]) + '\n')
                if (n * i + j) not in targets and i != j:
                    knowsObs.append('\t'.join(
                        [str(i), str(j), str(knowsMatrix[i][j])]) + '\n')
                elif (n * i + j) in targets and i != j:
                    knowsTarget.append('\t'.join([str(i), str(j)]) + '\n')
                if (n * i + j) in targets and i != j:
                    knowsTruth.append('\t'.join(
                        [str(i), str(j), str(knowsMatrix[i][j])]) + '\n')

        livedObs = []
        for person in people:
            for index, place in enumerate(person.lived[0]):
                if place == 1:
                    livedObs.append(
                        '\t'.join([str(person.index), str(index)]) + '\n')

        likesObs = []
        for person in people:
            for globalLike in person.globalLikes:
                likesObs.append('\t'.join([str(person.index), str(
                    globalLike), str(person.globalLikes[globalLike])]) + '\n')

        # NOTE: Both global and local likes are  combined in a single list
        # called likes.
        for person in people:
            for localLike in person.localLikes:
                likesObs.append('\t'.join([str(person.index), str(
                    self.globalThings.size + localLike), str(person.localLikes[localLike])]) + '\n')

        data = {
            'knowsData': knowsData,
            'knowsObs': knowsObs,
            'knowsTarget': knowsTarget,
            'knowsTruth': knowsTruth,
            'livedObs': livedObs,
            'likesObs': likesObs}
        return data

    def generateData(self):
        """Driver function to generate all truth data."""
        # 1. Generate people
        people = self.__generatePeople()
        # 2. Generate  Knows truth data
        knowsMatrix = self.__generateKnows(people)
        # 3. Write data to files
        data = self.__prepareDataToWrite(knowsMatrix, people)
        paths = {
            'knowsData': 'knows_data.txt',
            'knowsObs': 'knows_obs.txt',
            'knowsTarget': 'knows_targets.txt',
            'knowsTruth': 'knows_truth.txt',
            'livedObs': 'lived_obs.txt',
            'likesObs': 'likes_obs.txt'}
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
        args.localLikesVariance)
    datagen.generateData()
