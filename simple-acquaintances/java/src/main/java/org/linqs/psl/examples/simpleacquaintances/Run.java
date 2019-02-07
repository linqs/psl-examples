package org.linqs.psl.examples.simpleacquaintances;

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
import org.linqs.psl.evaluation.statistics.DiscreteEvaluator;
import org.linqs.psl.evaluation.statistics.Evaluator;
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

/**
 * A simple example.
 * In this example, we try to determine if two people know each other.
 * The model uses two features: where the people lived and what they like.
 * The model also has options to include symmetry and transitivity rules.
 */
public class Run {
    private static final String PARTITION_OBSERVATIONS = "observations";
    private static final String PARTITION_TARGETS = "targets";
    private static final String PARTITION_TRUTH = "truth";

    private static final String DATA_PATH = Paths.get("..", "data").toString();
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
        model.addPredicate("Lived", ConstantType.UniqueStringID, ConstantType.UniqueStringID);
        model.addPredicate("Likes", ConstantType.UniqueStringID, ConstantType.UniqueStringID);
        model.addPredicate("Knows", ConstantType.UniqueStringID, ConstantType.UniqueStringID);
    }

    /**
     * Defines the rules for this model.
     */
    private void defineRules() {
        log.info("Defining model rules");

        model.addRule("20: Lived(P1, L) & Lived(P2, L) & (P1 != P2) -> Knows(P1, P2) ^2");
        model.addRule("5: Lived(P1, L1) & Lived(P2, L2) & (P1 != P2) & (L1 != L2) -> !Knows(P1, P2) ^2");
        model.addRule("10: Likes(P1, L) & Likes(P2, L) & (P1 != P2) -> Knows(P1, P2) ^2");
        model.addRule("5: Knows(P1, P2) & Knows(P2, P3) & (P1 != P3) -> Knows(P1, P3) ^2");
        model.addRule("Knows(P1, P2) = Knows(P2, P1) .");
        model.addRule("5: !Knows(P1, P2) ^2");

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

        Inserter inserter = dataStore.getInserter(model.getStandardPredicate("Lived"), obsPartition);
        inserter.loadDelimitedData(Paths.get(DATA_PATH, "lived_obs.txt").toString());

        inserter = dataStore.getInserter(model.getStandardPredicate("Likes"), obsPartition);
        inserter.loadDelimitedDataTruth(Paths.get(DATA_PATH, "likes_obs.txt").toString());

        inserter = dataStore.getInserter(model.getStandardPredicate("Knows"), obsPartition);
        inserter.loadDelimitedData(Paths.get(DATA_PATH, "knows_obs.txt").toString());

        inserter = dataStore.getInserter(model.getStandardPredicate("Knows"), targetsPartition);
        inserter.loadDelimitedData(Paths.get(DATA_PATH, "knows_targets.txt").toString());

        inserter = dataStore.getInserter(model.getStandardPredicate("Knows"), truthPartition);
        inserter.loadDelimitedDataTruth(Paths.get(DATA_PATH, "knows_truth.txt").toString());
    }

    /**
     * Run inference to infer the unknown Knows relationships between people.
     */
    private void runInference(Partition obsPartition, Partition targetsPartition) {
        log.info("Starting inference");

        StandardPredicate[] closedPredicates = new StandardPredicate[]{model.getStandardPredicate("Lived"), model.getStandardPredicate("Likes")};
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

        (new File(OUTPUT_PATH)).mkdirs();
        FileWriter writer = new FileWriter(Paths.get(OUTPUT_PATH, "KNOWS.txt").toString());

        for (GroundAtom atom : resultsDB.getAllGroundAtoms(model.getStandardPredicate("Knows"))) {
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
     */
    private void evalResults(Partition targetsPartition, Partition truthPartition) {
        StandardPredicate[] closedPredicates = new StandardPredicate[]{model.getStandardPredicate("Lived"), model.getStandardPredicate("Likes")};
        Database resultsDB = dataStore.getDatabase(targetsPartition, closedPredicates);
        Database truthDB = dataStore.getDatabase(truthPartition, new StandardPredicate[]{model.getStandardPredicate("Knows")});

        Evaluator eval = new DiscreteEvaluator();
        eval.compute(resultsDB, truthDB, model.getStandardPredicate("Knows"));
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
        try {
            writeOutput(targetsPartition);
        } catch (IOException ex) {
            throw new RuntimeException("Unable to write out results.", ex);
        }
        evalResults(targetsPartition, truthPartition);

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
