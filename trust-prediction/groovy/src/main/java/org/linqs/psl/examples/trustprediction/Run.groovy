package org.linqs.psl.examples.trustprediction;

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
import org.linqs.psl.evaluation.statistics.RankingEvaluator;
import org.linqs.psl.groovy.PSLModel;
import org.linqs.psl.model.atom.GroundAtom;
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

    private static final String DATA_PATH = Paths.get("..", "data", "trust-prediction").toString();
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
        model.add predicate: "Trusts", types: [ConstantType.UniqueIntID, ConstantType.UniqueIntID];
        model.add predicate: "Knows", types: [ConstantType.UniqueIntID, ConstantType.UniqueIntID];
        model.add predicate: "Prior", types: [ConstantType.UniqueIntID];
    }

    /**
     * Defines the rules for this model.
     */
    private void defineRules() {
        log.info("Defining model rules");

        // We will just add all the rules in bulk.
        // Note that comments are allowed.
        model.addRules("""
            // FFpp
            1.0: Knows(A, B) & Knows(B, C) & Knows(A, C) & Trusts(A, B) & Trusts(B, C) & (A != B) & (B != C) & (A != C) -> Trusts(A, C) ^2
            // FFpm
            1.0: Knows(A, B) & Knows(B, C) & Knows(A, C) & Trusts(A, B) & !Trusts(B, C) & (A != B) & (B != C) & (A != C) -> !Trusts(A, C) ^2
            // FFmp
            1.0: Knows(A, B) & Knows(B, C) & Knows(A, C) & !Trusts(A, B) & Trusts(B, C) & (A != B) & (B != C) & (A != C) -> !Trusts(A, C) ^2
            // FFmm
            1.0: Knows(A, B) & Knows(B, C) & Knows(A, C) & !Trusts(A, B) & !Trusts(B, C) & (A != B) & (B != C) & (A != C) -> Trusts(A, C) ^2

            // FBpp
            1.0: Knows(A, B) & Knows(C, B) & Knows(A, C) & Trusts(A, B) & Trusts(C, B) & (A != B) & (B != C) & (A != C) -> Trusts(A, C)
            // FBpm
            1.0: Knows(A, B) & Knows(C, B) & Knows(A, C) & Trusts(A, B) & !Trusts(C, B) & (A != B) & (B != C) & (A != C) -> !Trusts(A, C) ^2
            // FBmp
            1.0: Knows(A, B) & Knows(C, B) & Knows(A, C) & !Trusts(A, B) & Trusts(C, B) & (A != B) & (B != C) & (A != C) -> !Trusts(A, C) ^2
            // FBmm
            1.0: Knows(A, B) & Knows(C, B) & Knows(A, C) & !Trusts(A, B) & !Trusts(C, B) & (A != B) & (B != C) & (A != C) -> Trusts(A, C) ^2

            // BFpp
            1.0: Knows(B, A) & Knows(B, C) & Knows(A, C) & Trusts(B, A) & Trusts(B, C) & (A != B) & (B != C) & (A != C) -> Trusts(A, C) ^2
            // BFpm
            1.0: Knows(B, A) & Knows(B, C) & Knows(A, C) & Trusts(B, A) & !Trusts(B, C) & (A != B) & (B != C) & (A != C) -> !Trusts(A, C) ^2
            // BFmp
            1.0: Knows(B, A) & Knows(B, C) & Knows(A, C) & !Trusts(B, A) & Trusts(B, C) & (A != B) & (B != C) & (A != C) -> !Trusts(A, C) ^2
            // BFmm
            1.0: Knows(B, A) & Knows(B, C) & Knows(A, C) & !Trusts(B, A) & !Trusts(B, C) & (A != B) & (B != C) & (A != C) -> Trusts(A, C) ^2

            // BBpp
            1.0: Knows(B, A) & Knows(C, B) & Knows(A, C) & Trusts(B, A) & Trusts(C, B) & (A != B) & (B != C) & (A != C) -> Trusts(A, C) ^2
            // BBpm
            1.0: Knows(B, A) & Knows(C, B) & Knows(A, C) & Trusts(B, A) & !Trusts(C, B) & (A != B) & (B != C) & (A != C) -> !Trusts(A, C) ^2
            // BBmp
            1.0: Knows(B, A) & Knows(C, B) & Knows(A, C) & !Trusts(B, A) & Trusts(C, B) & (A != B) & (B != C) & (A != C) -> !Trusts(A, C) ^2
            // BBmm
            1.0: Knows(B, A) & Knows(C, B) & Knows(A, C) & !Trusts(B, A) & !Trusts(C, B) & (A != B) & (B != C) & (A != C) -> Trusts(A, C) ^2

            1.0: Knows(A, B) & Knows(B, A) & Trusts(A, B) -> Trusts(B, A) ^2
            1.0: Knows(A, B) & Knows(B, A) & !Trusts(A, B) -> !Trusts(B, A) ^2

            // two-sided prior
            1.0: Knows(A, B) & Prior('0') -> Trusts(A, B) ^2
            1.0: Knows(A, B) & Trusts(A, B) -> Prior('0') ^2
        """);

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

            Inserter inserter = dataStore.getInserter(Knows, obsPartition);
            inserter.loadDelimitedDataTruth(Paths.get(DATA_PATH, type, "knows_obs.txt").toString());

            inserter = dataStore.getInserter(Prior, obsPartition);
            inserter.loadDelimitedDataTruth(Paths.get(DATA_PATH, type, "prior_obs.txt").toString());

            inserter = dataStore.getInserter(Trusts, obsPartition);
            inserter.loadDelimitedDataTruth(Paths.get(DATA_PATH, type, "trusts_obs.txt").toString());

            inserter = dataStore.getInserter(Trusts, targetsPartition);
            inserter.loadDelimitedData(Paths.get(DATA_PATH, type, "trusts_target.txt").toString());

            inserter = dataStore.getInserter(Trusts, truthPartition);
            inserter.loadDelimitedDataTruth(Paths.get(DATA_PATH, type, "trusts_truth.txt").toString());
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

        // This database contains all the ground atoms (targets) that we want to infer.
        // It also includes the observed data (because we will run inference over this db).
        Database randomVariableDatabase = dataStore.getDatabase(targetsPartition, [Knows, Prior] as Set, obsPartition);

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
        Database inferDB = dataStore.getDatabase(targetsPartition, [Knows, Prior] as Set, obsPartition);

        InferenceApplication inference = new MPEInference(model, inferDB);
        inference.inference();

        inference.close();
        inferDB.close();

        log.info("Inference complete");
    }

    /**
     * Writes the output of the model into a file
     */
    private void writeOutput() {
        Database resultsDB = dataStore.getDatabase(dataStore.getPartition(PARTITION_EVAL_TARGETS));

        (new File(OUTPUT_PATH)).mkdirs();
        FileWriter writer = new FileWriter(Paths.get(OUTPUT_PATH, "TRUSTS.txt").toString());

        for (GroundAtom atom : resultsDB.getAllGroundAtoms(Trusts)) {
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
    private void evalResults() {
        // Because the truth data also includes observed data, we will make sure to include the observed
        // partition here.
        Database resultsDB = dataStore.getDatabase(dataStore.getPartition(PARTITION_EVAL_TARGETS),
                [Knows, Prior] as Set, dataStore.getPartition(PARTITION_EVAL_OBSERVATIONS));
        Database truthDB = dataStore.getDatabase(dataStore.getPartition(PARTITION_EVAL_TRUTH),
                dataStore.getRegisteredPredicates());

        Evaluator eval = new RankingEvaluator();
        eval.compute(resultsDB, truthDB, Trusts);
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
