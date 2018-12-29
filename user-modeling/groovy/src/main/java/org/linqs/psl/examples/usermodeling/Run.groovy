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
import org.linqs.psl.groovy.PSLModel;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.model.term.ConstantType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Paths;

public class Run {
    private static final String PARTITION_OBSERVATIONS = "observations";
    private static final String PARTITION_TARGETS = "targets";
    private static final String PARTITION_TRUTH = "truth";

    private static final String DATA_PATH = Paths.get("..", "data", "user-modeling").toString();
    private static final String OUTPUT_PATH = "inferred-predicates";

    private static Logger log = LoggerFactory.getLogger(Run.class)

    private DataStore dataStore;
    private PSLModel model;

    public Run() {
        String suffix = System.getProperty("user.name") + "@" + getHostname();
        String baseDBPath = Config.getString("dbpath", System.getProperty("java.io.tmpdir"));
        String dbPath = Paths.get(baseDBPath, this.getClass().getName() + "_" + suffix).toString();
        dataStore = new RDBMSDataStore(new H2DatabaseDriver(Type.Disk, dbPath, true));
        // dataStore = new RDBMSDataStore(new PostgreSQLDriver("psl", true));

        model = new PSLModel(this, dataStore);
    }

    /**
     * Defines the logical predicates used in this model
     */
    private void definePredicates() {
        model.add predicate: "Predicts", types: [ConstantType.UniqueStringID, ConstantType.UniqueStringID, ConstantType.UniqueStringID, ConstantType.UniqueStringID];
        model.add predicate: "Friend", types: [ConstantType.UniqueStringID, ConstantType.UniqueStringID];
        model.add predicate: "Likes", types: [ConstantType.UniqueStringID, ConstantType.UniqueStringID];
        model.add predicate: "Joins", types: [ConstantType.UniqueStringID, ConstantType.UniqueStringID];
        model.add predicate: "Has", types: [ConstantType.UniqueStringID, ConstantType.UniqueStringID];
        model.add predicate: "Is", types: [ConstantType.UniqueStringID, ConstantType.UniqueStringID, ConstantType.UniqueStringID];
    }

    /**
     * Defines the rules for this model.
     */
    private void defineRules() {
        log.info("Defining model rules");

        // Priors from local classifiers.
        model.add(
            rule: "50: Has(U, S) & Predicts(S, U, A, L) -> Is(U, A, L) ^2"
        );

        model.add(
            rule: "50: Has(U, S) & ~Predicts(S, U, A, L) -> ~Is(U, A, L) ^2"
        );

        // Collective Rules for relational signals.
        model.add(
            rule: "100: Joins(U, G) & Joins(V, G) & Is(V, A, L) -> Is(U, A, L) ^2"
        );

        model.add(
            rule: "100: Joins(U, G) & Joins(V, G) & ~Is(V, A, L) -> ~Is(U, A, L) ^2"
        );

        model.add(
            rule: "10: Likes(U, T) & Likes(V, T) & Is(V, A, L) -> Is(U, A, L) ^2"
        );

        model.add(
            rule: "10: Likes(U, T) & Likes(V, T) & ~Is(V, A, L) -> ~Is(U, A, L) ^2"
        );

        model.add(
            rule: "1: Friend(U, V) & Is(V, A, L)-> Is(U, A, L) ^2"
        );

        model.add(
            rule: "1: Friend(U, V) & ~Is(V, A, L)-> ~Is(U, A, L) ^2"
        );

        model.add(
            rule: "1: Friend(V, U) & Is(V, A, L)-> Is(U, A, L) ^2"
        );

        model.add(
            rule: "1: Friend(V, U) & ~Is(V, A, L)-> ~Is(U, A, L) ^2"
        );

        // Ensure that user has one attribute
        model.add(
            rule: "1: Is(U, A, +L) = 1"
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
    private void loadData(Partition obsPartition, Partition targetsPartition, Partition truthPartition) {
        log.info("Loading data into database");

        Inserter inserter = dataStore.getInserter(Predicts, obsPartition);
        inserter.loadDelimitedDataAutomatic(Paths.get(DATA_PATH, "local_predictor_obs.txt").toString());

        inserter = dataStore.getInserter(Friend, obsPartition);
        inserter.loadDelimitedDataAutomatic(Paths.get(DATA_PATH, "friend_obs.txt").toString());

        inserter = dataStore.getInserter(Likes, obsPartition);
        inserter.loadDelimitedDataAutomatic(Paths.get(DATA_PATH, "likes_obs.txt").toString());

        inserter = dataStore.getInserter(Joins, obsPartition);
        inserter.loadDelimitedDataAutomatic(Paths.get(DATA_PATH, "joins_obs.txt").toString());

        inserter = dataStore.getInserter(Has, obsPartition);
        inserter.loadDelimitedDataAutomatic(Paths.get(DATA_PATH, "has_obs.txt").toString());

        inserter = dataStore.getInserter(Is, obsPartition);
        inserter.loadDelimitedDataAutomatic(Paths.get(DATA_PATH, "user_train.txt").toString());

        inserter = dataStore.getInserter(Is, targetsPartition);
        inserter.loadDelimitedDataAutomatic(Paths.get(DATA_PATH, "user_target.txt").toString());

        inserter = dataStore.getInserter(Is, truthPartition);
        inserter.loadDelimitedDataAutomatic(Paths.get(DATA_PATH, "user_truth.txt").toString());
    }

    /**
     * Run inference to infer the unknown targets.
     */
    private void runInference(Partition obsPartition, Partition targetsPartition) {
        log.info("Starting inference");

        Set openPredicates = [Is] as Set;
        Set closedPredicates = dataStore.getRegisteredPredicates();
        closedPredicates.removeAll(openPredicates);

        Database inferDB = dataStore.getDatabase(targetsPartition, closedPredicates, obsPartition);

        InferenceApplication inference = new MPEInference(model, inferDB);
        inference.inference();

        inference.close();
        inferDB.close();

        log.info("Inference complete");
    }

    /**
     * Writes the output of the model into a file
     */
    private void writeOutput(Partition targetsPartition) {
        Database resultsDB = dataStore.getDatabase(targetsPartition);

        (new File(OUTPUT_PATH)).mkdirs();
        FileWriter writer = new FileWriter(Paths.get(OUTPUT_PATH, "IS.txt").toString());

        for (GroundAtom atom : resultsDB.getAllGroundAtoms(Is)) {
            for (Constant argument : atom.getArguments()) {
                writer.write(argument.toString() + "\t");
            }
            writer.write("" + atom.getValue() + "\n");
        }

        writer.close();
        resultsDB.close();
    }

    private void evalResults(Partition obsPartition, Partition targetsPartition, Partition truthPartition) {
        Set openPredicates = [Is] as Set;
        Set closedPredicates = dataStore.getRegisteredPredicates();
        closedPredicates.removeAll(openPredicates);

        Database resultsDB = dataStore.getDatabase(targetsPartition, closedPredicates, obsPartition);
        Database truthDB = dataStore.getDatabase(truthPartition,    dataStore.getRegisteredPredicates());

        Evaluator eval = new DiscreteEvaluator();
        eval.compute(resultsDB, truthDB, Is);
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
        writeOutput(targetsPartition);
        evalResults(obsPartition, targetsPartition, truthPartition);

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
