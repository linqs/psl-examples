package org.linqs.psl.examples.preferenceprediction;

import org.linqs.psl.application.inference.InferenceApplication;
import org.linqs.psl.application.inference.MPEInference;
import org.linqs.psl.application.learning.weight.WeightLearningApplication;
import org.linqs.psl.application.learning.weight.maxlikelihood.MaxLikelihoodMPE;
import org.linqs.psl.config.Config;
import org.linqs.psl.database.Database;
import org.linqs.psl.database.DataStore;
import org.linqs.psl.database.Partition;
import org.linqs.psl.database.loading.Inserter;
import org.linqs.psl.database.rdbms.driver.H2DatabaseDriver;
import org.linqs.psl.database.rdbms.driver.H2DatabaseDriver.Type;
import org.linqs.psl.database.rdbms.driver.PostgreSQLDriver;
import org.linqs.psl.database.rdbms.RDBMSDataStore;
import org.linqs.psl.evaluation.statistics.Evaluator;
import org.linqs.psl.evaluation.statistics.ContinuousEvaluator;
import org.linqs.psl.java.PSLModel;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.model.term.ConstantType;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

public class Run {
    private static final String PARTITION_LEARN_OBSERVATIONS = "learn_observations";
    private static final String PARTITION_LEARN_TARGETS = "learn_targets";
    private static final String PARTITION_LEARN_TRUTH = "learn_truth";

    private static final String PARTITION_EVAL_OBSERVATIONS = "eval_observations";
    private static final String PARTITION_EVAL_TARGETS = "eval_targets";
    private static final String PARTITION_EVAL_TRUTH = "eval_truth";

    private static final String DATA_PATH = Paths.get("..", "data", "preference-prediction").toString();
    private static final String OUTPUT_PATH = "inferred-predicates";

    private static Logger log = LoggerFactory.getLogger(Run.class);

    private DataStore dataStore;
    private PSLModel model;

    public Run() {
        String suffix = System.getProperty("user.name") + "@" + getHostname();
        String baseDBPath = Config.getString("dbpath", System.getProperty("java.io.tmpdir"));
        String dbPath = Paths.get(baseDBPath, this.getClass().getName() + "_" + suffix).toString();
        dataStore = new RDBMSDataStore(new H2DatabaseDriver(Type.Disk, dbPath, true));
        // dataStore = new RDBMSDataStore(new PostgreSQLDriver("psl", true));

        model = new PSLModel(dataStore);
    }

    /**
     * Defines the logical predicates used in this model.
     */
    private void definePredicates() {
        model.addPredicate("AvgJokeRatingObs", ConstantType.UniqueIntID);
        model.addPredicate("AvgUserRatingObs", ConstantType.UniqueIntID);
        model.addPredicate("Joke", ConstantType.UniqueIntID);
        model.addPredicate("RatingPrior", ConstantType.UniqueIntID);
        model.addPredicate("SimObsRating", ConstantType.UniqueIntID, ConstantType.UniqueIntID);
        model.addPredicate("User", ConstantType.UniqueIntID);
        model.addPredicate("Rating", ConstantType.UniqueIntID, ConstantType.UniqueIntID);
    }

    /**
     * Defines the rules for this model.
     */
    private void defineRules() {
        log.info("Defining model rules");

        // If J1,J2 have similar observed ratings, then U will rate them similarly
        model.addRule("1.0: SimObsRating(J1,J2) & Rating(U,J1) >> Rating(U,J2) ^2");

        // Ratings should concentrate around observed User/Joke averages
        model.addRule("1.0: User(U) & Joke(J) & AvgUserRatingObs(U) >> Rating(U,J) ^2");

        model.addRule("1.0: User(U) & Joke(J) & AvgJokeRatingObs(J) >> Rating(U,J) ^2");

        model.addRule("1.0: User(U) & Joke(J) & Rating(U,J) >> AvgUserRatingObs(U) ^2");

        model.addRule("1.0: User(U) & Joke(J) & Rating(U,J) >> AvgJokeRatingObs(J) ^2");

        // Two-sided prior
        model.addRule("1.0: User(U) & Joke(J) & RatingPrior('0') >> Rating(U, J) ^2");

        model.addRule("1.0: Rating(U,J) >> RatingPrior('0') ^2");

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

        String[] splits = new String[]{"learn", "eval"};
        for (String split : splits) {
            Partition obsPartition = dataStore.getPartition(split + "_observations");
            Partition targetsPartition = dataStore.getPartition(split + "_targets");
            Partition truthPartition = dataStore.getPartition(split + "_truth");

            Inserter inserter = dataStore.getInserter(model.getStandardPredicate("AvgJokeRatingObs"), obsPartition);
            inserter.loadDelimitedDataTruth(Paths.get(DATA_PATH, split, "avgJokeRatingObs_obs.txt").toString());

            inserter = dataStore.getInserter(model.getStandardPredicate("AvgUserRatingObs"), obsPartition);
            inserter.loadDelimitedDataTruth(Paths.get(DATA_PATH, split, "avgUserRatingObs_obs.txt").toString());

            inserter = dataStore.getInserter(model.getStandardPredicate("Joke"), obsPartition);
            inserter.loadDelimitedDataTruth(Paths.get(DATA_PATH, split, "joke_obs.txt").toString());

            inserter = dataStore.getInserter(model.getStandardPredicate("RatingPrior"), obsPartition);
            inserter.loadDelimitedDataTruth(Paths.get(DATA_PATH, split, "ratingPrior_obs.txt").toString());

            inserter = dataStore.getInserter(model.getStandardPredicate("SimObsRating"), obsPartition);
            inserter.loadDelimitedDataTruth(Paths.get(DATA_PATH, split, "simObsRating_obs.txt").toString());

            inserter = dataStore.getInserter(model.getStandardPredicate("User"), obsPartition);
            inserter.loadDelimitedDataTruth(Paths.get(DATA_PATH, split, "user_obs.txt").toString());

            inserter = dataStore.getInserter(model.getStandardPredicate("Rating"), obsPartition);
            inserter.loadDelimitedDataTruth(Paths.get(DATA_PATH, split, "rating_obs.txt").toString());

            inserter = dataStore.getInserter(model.getStandardPredicate("Rating"), targetsPartition);
            inserter.loadDelimitedData(Paths.get(DATA_PATH, split, "rating_targets.txt").toString());

            inserter = dataStore.getInserter(model.getStandardPredicate("Rating"), truthPartition);
            inserter.loadDelimitedDataTruth(Paths.get(DATA_PATH, split, "rating_truth.txt").toString());
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

        // Get all the predicates from the data store and remove the open ones.
        Set<StandardPredicate> closedPredicates = dataStore.getRegisteredPredicates();
        closedPredicates.remove(model.getStandardPredicate("Rating"));

        // This database contains all the ground atoms (targets) that we want to infer.
        // It also includes the observed data (because we will run inference over this db).
        Database randomVariableDatabase = dataStore.getDatabase(targetsPartition, closedPredicates, obsPartition);

        // This database only contains the true ground atoms.
        Database observedTruthDatabase = dataStore.getDatabase(truthPartition, dataStore.getRegisteredPredicates());

        WeightLearningApplication wla = new MaxLikelihoodMPE(model, randomVariableDatabase, observedTruthDatabase);
        wla.learn();

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

        // Get all the predicates from the data store and remove the open ones.
        Set<StandardPredicate> closedPredicates = dataStore.getRegisteredPredicates();
        closedPredicates.remove(model.getStandardPredicate("Rating"));

        Database inferDB = dataStore.getDatabase(targetsPartition, closedPredicates, obsPartition);

        InferenceApplication inference = new MPEInference(model, inferDB);
        inference.inference();

        inference.close();
        inferDB.close();

        log.info("Inference complete");
    }

    /**
     * Writes the output of the model into a file.
     */
    private void writeOutput() throws  IOException {
        Database resultsDB = dataStore.getDatabase(dataStore.getPartition(PARTITION_EVAL_TARGETS));

        (new File(OUTPUT_PATH)).mkdirs();
        FileWriter writer = new FileWriter(Paths.get(OUTPUT_PATH, "RATING.txt").toString());

        for (GroundAtom atom : resultsDB.getAllGroundAtoms(model.getStandardPredicate("Rating"))) {
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
        // Get all the predicates from the data store and remove the open ones.
        Set<StandardPredicate> closedPredicates = dataStore.getRegisteredPredicates();
        closedPredicates.remove(model.getStandardPredicate("Rating"));

        // Because the truth data also includes observed data, we will make sure to include the observed
        // partition here.
        Database resultsDB = dataStore.getDatabase(dataStore.getPartition(PARTITION_EVAL_TARGETS), closedPredicates, dataStore.getPartition(PARTITION_EVAL_OBSERVATIONS));
        Database truthDB = dataStore.getDatabase(dataStore.getPartition(PARTITION_EVAL_TRUTH), dataStore.getRegisteredPredicates());

        Evaluator eval = new ContinuousEvaluator();
        eval.compute(resultsDB, truthDB, model.getStandardPredicate("Rating"));
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
        try {
            writeOutput();
        } catch (IOException ex) {
            throw new RuntimeException("Unable to write out results.", ex);
        }
        evalResults();

        dataStore.close();
    }

    /**
     * Run this model from the command line.
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
