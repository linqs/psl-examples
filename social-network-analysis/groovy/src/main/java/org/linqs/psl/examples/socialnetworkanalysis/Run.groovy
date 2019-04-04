package org.linqs.psl.examples.socialnetworkanalysis;

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
import org.linqs.psl.groovy.PSLModel;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.model.term.ConstantType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final String DATA_PATH = Paths.get("..", "data", "social-network-analysis").toString();
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
     * Defines the logical predicates used in this model.
     */
    private void definePredicates() {
        model.add predicate: "Bias", types: [ConstantType.UniqueIntID, ConstantType.UniqueIntID];
        model.add predicate: "Boss", types: [ConstantType.UniqueIntID, ConstantType.UniqueIntID];
        model.add predicate: "Idol", types: [ConstantType.UniqueIntID, ConstantType.UniqueIntID];
        model.add predicate: "Knows", types: [ConstantType.UniqueIntID, ConstantType.UniqueIntID];
        model.add predicate: "KnowsWell", types: [ConstantType.UniqueIntID, ConstantType.UniqueIntID];
        model.add predicate: "Mentor", types: [ConstantType.UniqueIntID, ConstantType.UniqueIntID];
        model.add predicate: "OlderRelative", types: [ConstantType.UniqueIntID, ConstantType.UniqueIntID];
        model.add predicate: "Votes", types: [ConstantType.UniqueIntID, ConstantType.UniqueIntID];
    }

    /**
     * Defines the rules for this model.
     */
    private void defineRules() {
        log.info("Defining model rules");

        model.add(
            rule: "0.50: Bias(A, P) >> Votes(A, P) ^2"
        );

        model.add(
            rule: "0.30: Votes(A, P) & KnowsWell(B, A) >> Votes(B, P) ^2"
        );

        model.add(
            rule: "0.10: Votes(A, P) & Knows(B, A) >> Votes(B, P) ^2"
        );

        model.add(
            rule: "0.05: Votes(A, P) & Boss(B, A) >> Votes(B, P) ^2"
        );

        model.add(
            rule: "0.10: Votes(A, P) & Mentor(B, A) >> Votes(B, P) ^2"
        );

        model.add(
            rule: "0.70: Votes(A, P) & OlderRelative(B, A) >> Votes(B, P) ^2"
        );

        model.add(
            rule: "0.80: Votes(A, P) & Idol(B, A) >> Votes(B, P) ^2"
        );

        model.add(
            rule: "Votes(A, +B) <= 1 ."
        );

        model.add(
            rule: "0.01: !Votes(A, P) ^2"
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
    private void loadData(Partition obsPartition, Partition targetsPartition) {
        log.info("Loading data into database");

        Inserter inserter = dataStore.getInserter(Bias, obsPartition);
        inserter.loadDelimitedDataTruth(Paths.get(DATA_PATH, "bias_obs.txt").toString());

        inserter = dataStore.getInserter(Boss, obsPartition);
        inserter.loadDelimitedData(Paths.get(DATA_PATH, "boss_obs.txt").toString());

        inserter = dataStore.getInserter(Idol, obsPartition);
        inserter.loadDelimitedData(Paths.get(DATA_PATH, "idol_obs.txt").toString());

        inserter = dataStore.getInserter(Knows, obsPartition);
        inserter.loadDelimitedData(Paths.get(DATA_PATH, "knows_obs.txt").toString());

        inserter = dataStore.getInserter(KnowsWell, obsPartition);
        inserter.loadDelimitedData(Paths.get(DATA_PATH, "knowswell_obs.txt").toString());

        inserter = dataStore.getInserter(Mentor, obsPartition);
        inserter.loadDelimitedData(Paths.get(DATA_PATH, "mentor_obs.txt").toString());

        inserter = dataStore.getInserter(OlderRelative, obsPartition);
        inserter.loadDelimitedData(Paths.get(DATA_PATH, "olderRelative_obs.txt").toString());

        inserter = dataStore.getInserter(Votes, targetsPartition);
        inserter.loadDelimitedData(Paths.get(DATA_PATH, "votes_targets.txt").toString());
    }

    /**
     * Run inference to infer the unknown targets.
     */
    private void runInference(Partition obsPartition, Partition targetsPartition) {
        log.info("Starting inference");

        Set<StandardPredicate> closedPredicates = [
            Bias, Boss, Idol, Knows, KnowsWell, Mentor, OlderRelative
        ] as Set;

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
    private void writeOutput(Partition targetsPartition) {
        Database resultsDB = dataStore.getDatabase(targetsPartition);

        (new File(OUTPUT_PATH)).mkdirs();
        FileWriter writer = new FileWriter(Paths.get(OUTPUT_PATH, "VOTES.txt").toString());

        for (GroundAtom atom : resultsDB.getAllGroundAtoms(Votes)) {
            for (Constant argument : atom.getArguments()) {
                writer.write(argument.toString() + "\t");
            }
            writer.write("" + atom.getValue() + "\n");
        }

        writer.close();
        resultsDB.close();
    }

    public void run() {
        Partition obsPartition = dataStore.getPartition(PARTITION_OBSERVATIONS);
        Partition targetsPartition = dataStore.getPartition(PARTITION_TARGETS);

        definePredicates();
        defineRules();
        loadData(obsPartition, targetsPartition);
        runInference(obsPartition, targetsPartition);
        writeOutput(targetsPartition);

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
