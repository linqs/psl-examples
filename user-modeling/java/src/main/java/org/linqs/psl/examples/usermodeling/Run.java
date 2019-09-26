package org.linqs.psl.examples.usermodeling;

import org.linqs.psl.application.inference.InferenceApplication;
import org.linqs.psl.application.inference.MPEInference;
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
import org.linqs.psl.evaluation.statistics.DiscreteEvaluator;
import org.linqs.psl.java.PSLModel;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.model.term.ConstantType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Paths;
import java.util.Set;
import java.util.HashSet;

public class Run {
    private static final String PARTITION_OBSERVATIONS = "observations";
    private static final String PARTITION_TARGETS = "targets";
    private static final String PARTITION_TRUTH = "truth";

    private static final String DATA_PATH = Paths.get("..", "data", "user-modeling").toString();
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
        model.addPredicate("Predicts", ConstantType.UniqueStringID, ConstantType.UniqueStringID, ConstantType.UniqueStringID, ConstantType.UniqueStringID);
        model.addPredicate("Friend", ConstantType.UniqueStringID, ConstantType.UniqueStringID);
        model.addPredicate("Likes", ConstantType.UniqueStringID, ConstantType.UniqueStringID);
        model.addPredicate("Joins", ConstantType.UniqueStringID, ConstantType.UniqueStringID);
        model.addPredicate("Has", ConstantType.UniqueStringID, ConstantType.UniqueStringID);
        model.addPredicate("Is", ConstantType.UniqueStringID, ConstantType.UniqueStringID, ConstantType.UniqueStringID);
    }

    /**
     * Defines the rules for this model.
     */
    private void defineRules() {
        log.info("Defining model rules");

        // Priors from local classifiers.
        model.addRule("50: Has(U, S) & Predicts(S, U, A, L) -> Is(U, A, L) ^2");

        model.addRule("50: Has(U, S) & ~Predicts(S, U, A, L) -> ~Is(U, A, L) ^2");

        // Collective Rules for relational signals.
        model.addRule("100: Joins(U, G) & Joins(V, G) & Is(V, A, L) -> Is(U, A, L) ^2");

        model.addRule("100: Joins(U, G) & Joins(V, G) & ~Is(V, A, L) -> ~Is(U, A, L) ^2");

        model.addRule("10: Likes(U, T) & Likes(V, T) & Is(V, A, L) -> Is(U, A, L) ^2");

        model.addRule("10: Likes(U, T) & Likes(V, T) & ~Is(V, A, L) -> ~Is(U, A, L) ^2");

        model.addRule("1: Friend(U, V) & Is(V, A, L)-> Is(U, A, L) ^2");

        model.addRule("1: Friend(U, V) & ~Is(V, A, L)-> ~Is(U, A, L) ^2");

        model.addRule("1: Friend(V, U) & Is(V, A, L)-> Is(U, A, L) ^2");

        model.addRule("1: Friend(V, U) & ~Is(V, A, L)-> ~Is(U, A, L) ^2");

        // Ensure that user has one attribute
        model.addRule("1: Is(U, A, +L) = 1");

        log.debug("model: {}", model);
    }

    /**
     * Load data from text files into the DataStore.
     * Three partitions are defined and populated: observations, targets, and truth.
     * Observations contains evidence that we treat as background knowledge and use to condition our inferences.
     * Targets contains the inference targets - the unknown variables we wish to infer.
     * Truth contains the true values of the inference variables and will be used to evaluate the model's performance.
     */
    private void loadData(Partition obsPartition, Partition targetsPartition, Partition truthPartition) {
        log.info("Loading data into database");

        Inserter inserter = dataStore.getInserter(model.getStandardPredicate("Predicts"), obsPartition);
        inserter.loadDelimitedDataAutomatic(Paths.get(DATA_PATH, "local_predictor_obs.txt").toString());

        inserter = dataStore.getInserter(model.getStandardPredicate("Friend"), obsPartition);
        inserter.loadDelimitedDataAutomatic(Paths.get(DATA_PATH, "friend_obs.txt").toString());

        inserter = dataStore.getInserter(model.getStandardPredicate("Likes"), obsPartition);
        inserter.loadDelimitedDataAutomatic(Paths.get(DATA_PATH, "likes_obs.txt").toString());

        inserter = dataStore.getInserter(model.getStandardPredicate("Joins"), obsPartition);
        inserter.loadDelimitedDataAutomatic(Paths.get(DATA_PATH, "joins_obs.txt").toString());

        inserter = dataStore.getInserter(model.getStandardPredicate("Has"), obsPartition);
        inserter.loadDelimitedDataAutomatic(Paths.get(DATA_PATH, "has_obs.txt").toString());

        inserter = dataStore.getInserter(model.getStandardPredicate("Is"), obsPartition);
        inserter.loadDelimitedDataAutomatic(Paths.get(DATA_PATH, "user_train.txt").toString());

        inserter = dataStore.getInserter(model.getStandardPredicate("Is"), targetsPartition);
        inserter.loadDelimitedDataAutomatic(Paths.get(DATA_PATH, "user_target.txt").toString());

        inserter = dataStore.getInserter(model.getStandardPredicate("Is"), truthPartition);
        inserter.loadDelimitedDataAutomatic(Paths.get(DATA_PATH, "user_truth.txt").toString());
    }

    /**
     * Run inference to infer the unknown targets.
     */
    private void runInference(Partition obsPartition, Partition targetsPartition) {
        log.info("Starting inference");

        Set<StandardPredicate> openPredicates = new HashSet<StandardPredicate>();
        openPredicates.add(model.getStandardPredicate("Is"));
        Set<StandardPredicate> closedPredicates = dataStore.getRegisteredPredicates();
        closedPredicates.removeAll(openPredicates);

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
    private void writeOutput(Partition targetsPartition) throws IOException {
        Database resultsDB = dataStore.getDatabase(targetsPartition);

        FileWriter writer = new FileWriter(Paths.get(OUTPUT_PATH, "IS.txt").toString());

        for (GroundAtom atom : resultsDB.getAllGroundAtoms(model.getStandardPredicate("Is"))) {
            for (Constant argument : atom.getArguments()) {
                writer.write(argument.toString() + "\t");
                }
            writer.write("" + atom.getValue() + "\n");
        }
        writer.close();
        resultsDB.close();
    }

    private void evalResults(Partition obsPartition, Partition targetsPartition, Partition truthPartition) {
        Set<StandardPredicate> openPredicates = new HashSet<StandardPredicate>();
        openPredicates.add(model.getStandardPredicate("Is"));

        Set<StandardPredicate> closedPredicates = dataStore.getRegisteredPredicates();
        closedPredicates.removeAll(openPredicates);

        Database resultsDB = dataStore.getDatabase(targetsPartition, closedPredicates, obsPartition);
        Database truthDB = dataStore.getDatabase(truthPartition, dataStore.getRegisteredPredicates());

        Evaluator eval = new DiscreteEvaluator();
        eval.compute(resultsDB, truthDB, model.getStandardPredicate("Is"));
        log.info(eval.getAllStats());

        resultsDB.close();
        truthDB.close();
    }

    public void run() {
        Partition obsPartition = dataStore.getPartition(PARTITION_OBSERVATIONS);
        Partition targetsPartition = dataStore.getPartition(PARTITION_TARGETS);
        Partition truthPartition = dataStore.getPartition(PARTITION_TRUTH);

        definePredicates();
        defineRules();
        loadData(obsPartition, targetsPartition, truthPartition);

        runInference(obsPartition, targetsPartition);
        try{
            writeOutput(targetsPartition);
            } catch (IOException ex) {
            throw new RuntimeException("Unable to write out results.", ex);
        }
        evalResults(obsPartition, targetsPartition, truthPartition);

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
