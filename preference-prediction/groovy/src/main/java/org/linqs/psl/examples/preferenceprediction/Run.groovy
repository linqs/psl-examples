package org.linqs.psl.examples.preferenceprediction;

import org.linqs.psl.application.inference.MPEInference;
import org.linqs.psl.application.learning.weight.VotedPerceptron;
import org.linqs.psl.application.learning.weight.maxlikelihood.MaxLikelihoodMPE;
import org.linqs.psl.config.ConfigBundle;
import org.linqs.psl.config.ConfigManager;
import org.linqs.psl.database.Database;
import org.linqs.psl.database.DataStore;
import org.linqs.psl.database.Partition;
import org.linqs.psl.database.Queries;
import org.linqs.psl.database.loading.Inserter;
import org.linqs.psl.database.rdbms.driver.H2DatabaseDriver;
import org.linqs.psl.database.rdbms.driver.H2DatabaseDriver.Type;
import org.linqs.psl.database.rdbms.driver.PostgreSQLDriver;
import org.linqs.psl.database.rdbms.RDBMSDataStore;
import org.linqs.psl.evaluation.statistics.Evaluator;
import org.linqs.psl.evaluation.statistics.ContinuousEvaluator;
import org.linqs.psl.groovy.PSLModel;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.model.term.ConstantType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Paths;

public class Run {
	private static final String PARTITION_LEARN_OBSERVATIONS = "learn_observations";
	private static final String PARTITION_LEARN_TARGETS = "learn_targets";
	private static final String PARTITION_LEARN_TRUTH = "learn_truth";

	private static final String PARTITION_EVAL_OBSERVATIONS = "eval_observations";
	private static final String PARTITION_EVAL_TARGETS = "eval_targets";
	private static final String PARTITION_EVAL_TRUTH = "eval_truth";

	private static final String DATA_PATH = Paths.get("..", "data", "preference-prediction").toString();
	private static final String OUTPUT_PATH = "inferred-predicates";

	private static Logger log = LoggerFactory.getLogger(Run.class)

	private DataStore dataStore;
	private ConfigBundle config;
	private PSLModel model;

	public Run() {
		config = ConfigManager.getManager().getBundle("preferenceprediction");

		String suffix = System.getProperty("user.name") + "@" + getHostname();
		String baseDBPath = config.getString("dbpath", System.getProperty("java.io.tmpdir"));
		String dbPath = Paths.get(baseDBPath, this.getClass().getName() + "_" + suffix).toString();
		dataStore = new RDBMSDataStore(new H2DatabaseDriver(Type.Disk, dbPath, true), config);
		// dataStore = new RDBMSDataStore(new PostgreSQLDriver("psl", true), config);

		model = new PSLModel(this, dataStore);
	}

	/**
	 * Defines the logical predicates used in this model
	 */
	private void definePredicates() {
		model.add predicate: "AvgJokeRatingObs", types: [ConstantType.UniqueIntID];
		model.add predicate: "AvgUserRatingObs", types: [ConstantType.UniqueIntID];
		model.add predicate: "Joke", types: [ConstantType.UniqueIntID];
		model.add predicate: "RatingPrior", types: [ConstantType.UniqueIntID];
		model.add predicate: "SimObsRating", types: [ConstantType.UniqueIntID, ConstantType.UniqueIntID];
		model.add predicate: "User", types: [ConstantType.UniqueIntID];
		model.add predicate: "Rating", types: [ConstantType.UniqueIntID, ConstantType.UniqueIntID];
	}

	/**
	 * Defines the rules for this model.
	 */
	private void defineRules() {
		log.info("Defining model rules");

		// If J1,J2 have similar observed ratings, then U will rate them similarly
		model.add(
			rule: "1.0: SimObsRating(J1,J2) & Rating(U,J1) >> Rating(U,J2) ^2"
		);

		// Ratings should concentrate around observed User/Joke averages
		model.add(
			rule: "1.0: User(U) & Joke(J) & AvgUserRatingObs(U) >> Rating(U,J) ^2"
		);

		model.add(
			rule: "1.0: User(U) & Joke(J) & AvgJokeRatingObs(J) >> Rating(U,J) ^2"
		);

		model.add(
			rule: "1.0: User(U) & Joke(J) & Rating(U,J) >> AvgUserRatingObs(U) ^2"
		);

		model.add(
			rule: "1.0: User(U) & Joke(J) & Rating(U,J) >> AvgJokeRatingObs(J) ^2"
		);

		// Two-sided prior
		model.add(
			rule: "1.0: User(U) & Joke(J) & RatingPrior('0') >> Rating(U, J) ^2"
		);

		model.add(
			rule: "1.0: Rating(U,J) >> RatingPrior('0') ^2"
		);

		log.debug("model: {}", model);
	}

	/**
	 * Load data from text files into the DataStore.
	 * Three partitions are defined and populated: observations, targets, and truth.
	 * Observations contains evidence that we treat as background knowledge and use to condition our inferences.
	 * Targets contains the inference targets - the unknown variables we wish to infer.
	 * Truth contains the true values of the inference variables and will be used to evaluate the model's performance.
	 */
	private void loadData() {
		log.info("Loading data into database");

		for (String type : ["learn", "eval"]) {
			Partition obsPartition = dataStore.getPartition(type + "_observations");
			Partition targetsPartition = dataStore.getPartition(type + "_targets");
			Partition truthPartition = dataStore.getPartition(type + "_truth");

			Inserter inserter = dataStore.getInserter(AvgJokeRatingObs, obsPartition);
			inserter.loadDelimitedDataTruth(Paths.get(DATA_PATH, type, "avgJokeRatingObs_obs.txt").toString());

			inserter = dataStore.getInserter(AvgUserRatingObs, obsPartition);
			inserter.loadDelimitedDataTruth(Paths.get(DATA_PATH, type, "avgUserRatingObs_obs.txt").toString());

			inserter = dataStore.getInserter(Joke, obsPartition);
			inserter.loadDelimitedDataTruth(Paths.get(DATA_PATH, type, "joke_obs.txt").toString());

			inserter = dataStore.getInserter(RatingPrior, obsPartition);
			inserter.loadDelimitedDataTruth(Paths.get(DATA_PATH, type, "ratingPrior_obs.txt").toString());

			inserter = dataStore.getInserter(SimObsRating, obsPartition);
			inserter.loadDelimitedDataTruth(Paths.get(DATA_PATH, type, "simObsRating_obs.txt").toString());

			inserter = dataStore.getInserter(User, obsPartition);
			inserter.loadDelimitedDataTruth(Paths.get(DATA_PATH, type, "user_obs.txt").toString());

			inserter = dataStore.getInserter(Rating, obsPartition);
			inserter.loadDelimitedDataTruth(Paths.get(DATA_PATH, type, "rating_obs.txt").toString());

			inserter = dataStore.getInserter(Rating, targetsPartition);
			inserter.loadDelimitedData(Paths.get(DATA_PATH, type, "rating_targets.txt").toString());

			inserter = dataStore.getInserter(Rating, truthPartition);
			inserter.loadDelimitedDataTruth(Paths.get(DATA_PATH, type, "rating_truth.txt").toString());
		}
	}

	/**
	 * Use the training data to learn weights for our rules and store them back in the model object.
	 */
	private void learnWeights() {
		log.info("Starting weight learning");

		Partition obsPartition = dataStore.getPartition(PARTITION_LEARN_OBSERVATIONS);
		Partition targetsPartition = dataStore.getPartition(PARTITION_LEARN_TARGETS);
		Partition truthPartition = dataStore.getPartition(PARTITION_LEARN_TRUTH);

		Set<StandardPredicate> closedPredicates = [AvgJokeRatingObs, AvgUserRatingObs, Joke, RatingPrior, SimObsRating, User] as Set;

		// This database contains all the ground atoms (targets) that we want to infer.
		// It also includes the observed data (because we will run inference over this db).
		Database randomVariableDatabase = dataStore.getDatabase(targetsPartition, closedPredicates, obsPartition);

		// This database only contains the true ground atoms.
		Database observedTruthDatabase = dataStore.getDatabase(truthPartition, dataStore.getRegisteredPredicates());

		VotedPerceptron vp = new MaxLikelihoodMPE(model, randomVariableDatabase, observedTruthDatabase, config);
		vp.learn();

		randomVariableDatabase.close();
		observedTruthDatabase.close();

		log.info("Weight learning complete");
	}

	/**
	 * Run inference to infer the unknown HasCat relationships between people.
	 */
	private void runInference() {
		log.info("Starting inference");

		Partition obsPartition = dataStore.getPartition(PARTITION_EVAL_OBSERVATIONS);
		Partition targetsPartition = dataStore.getPartition(PARTITION_EVAL_TARGETS);

		Set<StandardPredicate> closedPredicates = [AvgJokeRatingObs, AvgUserRatingObs, Joke, RatingPrior, SimObsRating, User] as Set;

		Database inferDB = dataStore.getDatabase(targetsPartition, closedPredicates, obsPartition);

		MPEInference mpe = new MPEInference(model, inferDB, config);
		mpe.mpeInference();

		mpe.close();
		inferDB.close();

		log.info("Inference complete");
	}

	/**
	 * Writes the output of the model into a file
	 */
	private void writeOutput() {
		Database resultsDB = dataStore.getDatabase(dataStore.getPartition(PARTITION_EVAL_TARGETS));

		(new File(OUTPUT_PATH)).mkdirs();
		FileWriter writer = new FileWriter(Paths.get(OUTPUT_PATH, "RATING.txt").toString());

		for (GroundAtom atom : Queries.getAllAtoms(resultsDB, Rating)) {
			for (Constant argument : atom.getArguments()) {
				writer.write(argument.toString() + "\t");
			}
			writer.write("" + atom.getValue() + "\n");
		}

		writer.close();
		resultsDB.close();
	}

	/**
	 * Run statistical evaluation scripts to determine the quality of the inferences
	 * relative to the defined truth.
	 * Note that the target predicate is categorical and we will assign the category with the
	 * highest truth value as true and the rest false.
	 */
	private void evalResults() {
		Set<StandardPredicate> closedPredicates = [AvgJokeRatingObs, AvgUserRatingObs, Joke, RatingPrior, SimObsRating, User] as Set;

		// Because the truth data also includes observed data, we will make sure to include the observed
		// partition here.
		Database resultsDB = dataStore.getDatabase(dataStore.getPartition(PARTITION_EVAL_TARGETS),
				closedPredicates, dataStore.getPartition(PARTITION_EVAL_OBSERVATIONS));
		Database truthDB = dataStore.getDatabase(dataStore.getPartition(PARTITION_EVAL_TRUTH),
				dataStore.getRegisteredPredicates());

		Evaluator eval = new ContinuousEvaluator();
		eval.compute(resultsDB, truthDB, Rating);
		log.info(eval.getAllStats());

		resultsDB.close();
		truthDB.close();
	}

	public void run() {
		definePredicates();
		defineRules();
		loadData();

		learnWeights();
		runInference();

		writeOutput();
		evalResults();

		dataStore.close();
	}

	/**
	 * Run this model from the command line
	 * @param args - the command line arguments
	 */
	public static void main(String[] args) {
		Run run = new Run();
		run.run();
	}

	private static String getHostname() {
		String hostname = "unknown";

		try {
			hostname = InetAddress.getLocalHost().getHostName();
		} catch (UnknownHostException ex) {
			log.warn("Hostname can not be resolved, using '" + hostname + "'.");
		}

		return hostname;
	}
}
