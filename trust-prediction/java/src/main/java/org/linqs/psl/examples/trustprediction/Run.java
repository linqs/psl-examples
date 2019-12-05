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
import org.linqs.psl.java.PSLModel;
import org.linqs.psl.model.atom.GroundAtom;
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
import java.util.Set;
import java.util.HashSet;

public class Run {
    private static final String PARTITION_LEARN_OBSERVATIONS = "learn_observations";
    private static final String PARTITION_LEARN_TARGETS = "learn_targets";
    private static final String PARTITION_LEARN_TRUTH = "learn_truth";

    private static final String PARTITION_EVAL_OBSERVATIONS = "eval_observations";
    private static final String PARTITION_EVAL_TARGETS = "eval_targets";
    private static final String PARTITION_EVAL_TRUTH = "eval_truth";

    private static final String DATA_PATH = Paths.get("..", "data", "trust-prediction").toString();
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
     * Defines the logical predicates used in this model
     */
    private void definePredicates() {
        model.addPredicate("Trusts", ConstantType.UniqueIntID, ConstantType.UniqueIntID);
        model.addPredicate("Knows", ConstantType.UniqueIntID, ConstantType.UniqueIntID);
        model.addPredicate("Prior", ConstantType.UniqueIntID);
    }

    /**
     * Defines the rules for this model.
     */
    private void defineRules() {
        log.info("Defining model rules");

        // We will just add all the rules in bulk.

        model.addRules(
                "1.0: Knows(A, B) & Knows(B, C) & Knows(A, C) & Trusts(A, B) & Trusts(B, C) & (A != B) & (B != C) & (A != C) -> Trusts(A, C) ^2" +
                "1.0: Knows(A, B) & Knows(B, C) & Knows(A, C) & Trusts(A, B) & !Trusts(B, C) & (A != B) & (B != C) & (A != C) -> !Trusts(A, C) ^2" +
                "1.0: Knows(A, B) & Knows(B, C) & Knows(A, C) & !Trusts(A, B) & Trusts(B, C) & (A != B) & (B != C) & (A != C) -> !Trusts(A, C) ^2" +
                "1.0: Knows(A, B) & Knows(B, C) & Knows(A, C) & !Trusts(A, B) & !Trusts(B, C) & (A != B) & (B != C) & (A != C) -> Trusts(A, C) ^2" +
                "1.0: Knows(A, B) & Knows(C, B) & Knows(A, C) & Trusts(A, B) & Trusts(C, B) & (A != B) & (B != C) & (A != C) -> Trusts(A, C)" +
                "1.0: Knows(A, B) & Knows(C, B) & Knows(A, C) & Trusts(A, B) & !Trusts(C, B) & (A != B) & (B != C) & (A != C) -> !Trusts(A, C) ^2" +
                "1.0: Knows(A, B) & Knows(C, B) & Knows(A, C) & !Trusts(A, B) & Trusts(C, B) & (A != B) & (B != C) & (A != C) -> !Trusts(A, C) ^2" +
                "1.0: Knows(A, B) & Knows(C, B) & Knows(A, C) & !Trusts(A, B) & !Trusts(C, B) & (A != B) & (B != C) & (A != C) -> Trusts(A, C) ^2" +
                "1.0: Knows(B, A) & Knows(B, C) & Knows(A, C) & Trusts(B, A) & Trusts(B, C) & (A != B) & (B != C) & (A != C) -> Trusts(A, C) ^2" +
                "1.0: Knows(B, A) & Knows(B, C) & Knows(A, C) & Trusts(B, A) & !Trusts(B, C) & (A != B) & (B != C) & (A != C) -> !Trusts(A, C) ^2" +
                "1.0: Knows(B, A) & Knows(B, C) & Knows(A, C) & !Trusts(B, A) & Trusts(B, C) & (A != B) & (B != C) & (A != C) -> !Trusts(A, C) ^2" +
                "1.0: Knows(B, A) & Knows(B, C) & Knows(A, C) & !Trusts(B, A) & !Trusts(B, C) & (A != B) & (B != C) & (A != C) -> Trusts(A, C) ^2" +
                "1.0: Knows(B, A) & Knows(C, B) & Knows(A, C) & Trusts(B, A) & Trusts(C, B) & (A != B) & (B != C) & (A != C) -> Trusts(A, C) ^2" +
                "1.0: Knows(B, A) & Knows(C, B) & Knows(A, C) & Trusts(B, A) & !Trusts(C, B) & (A != B) & (B != C) & (A != C) -> !Trusts(A, C) ^2" +
                "1.0: Knows(B, A) & Knows(C, B) & Knows(A, C) & !Trusts(B, A) & Trusts(C, B) & (A != B) & (B != C) & (A != C) -> !Trusts(A, C) ^2" +
                "1.0: Knows(B, A) & Knows(C, B) & Knows(A, C) & !Trusts(B, A) & !Trusts(C, B) & (A != B) & (B != C) & (A != C) -> Trusts(A, C) ^2" +
                "1.0: Knows(A, B) & Knows(B, A) & Trusts(A, B) -> Trusts(B, A) ^2" +
                "1.0: Knows(A, B) & Knows(B, A) & !Trusts(A, B) -> !Trusts(B, A) ^2" +
                "1.0: Knows(A, B) & Prior('0') -> Trusts(A, B) ^2" +
                "1.0: Knows(A, B) & Trusts(A, B) -> Prior('0') ^2");

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

        String[] splits = {"learn", "eval"};
        for (String split : splits) {
            Partition obsPartition = dataStore.getPartition(split + "_observations");
            Partition targetsPartition = dataStore.getPartition(split + "_targets");
            Partition truthPartition = dataStore.getPartition(split + "_truth");

            Inserter inserter = dataStore.getInserter(model.getStandardPredicate("Knows"), obsPartition);
            inserter.loadDelimitedDataTruth(Paths.get(DATA_PATH, split, "knows_obs.txt").toString());

            inserter = dataStore.getInserter(model.getStandardPredicate("Prior"), obsPartition);
            inserter.loadDelimitedDataTruth(Paths.get(DATA_PATH, split, "prior_obs.txt").toString());

            inserter = dataStore.getInserter(model.getStandardPredicate("Trusts"), obsPartition);
            inserter.loadDelimitedDataTruth(Paths.get(DATA_PATH, split, "trusts_obs.txt").toString());

            inserter = dataStore.getInserter(model.getStandardPredicate("Trusts"), targetsPartition);
            inserter.loadDelimitedData(Paths.get(DATA_PATH, split, "trusts_target.txt").toString());

            inserter = dataStore.getInserter(model.getStandardPredicate("Trusts"), truthPartition);
            inserter.loadDelimitedDataTruth(Paths.get(DATA_PATH, split, "trusts_truth.txt").toString());
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
        Set<StandardPredicate> set = new HashSet<StandardPredicate>();
        set.add(model.getStandardPredicate("Knows"));
        set.add(model.getStandardPredicate("Prior"));
        Database randomVariableDatabase = dataStore.getDatabase(targetsPartition, set, obsPartition);

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

        Set<StandardPredicate> set = new HashSet<StandardPredicate>();
        set.add(model.getStandardPredicate("Knows"));
        set.add(model.getStandardPredicate("Prior"));
        Database inferDB = dataStore.getDatabase(targetsPartition, set, obsPartition);

        InferenceApplication inference = new MPEInference(model, inferDB);
        inference.inference();

        inference.close();
        inferDB.close();

        log.info("Inference complete");
    }

    /**
     * Writes the output of the model into a file.
     */
    private void writeOutput() throws IOException {
       Database resultsDB = dataStore.getDatabase(dataStore.getPartition(PARTITION_EVAL_TARGETS));

       (new File(OUTPUT_PATH)).mkdirs();
       FileWriter writer = new FileWriter(Paths.get(OUTPUT_PATH, "TRUSTS.txt").toString());

       for (GroundAtom atom : resultsDB.getAllGroundAtoms(model.getStandardPredicate("Trusts"))) {
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
        Set<StandardPredicate> set = new HashSet<StandardPredicate>();
        set.add(model.getStandardPredicate("Knows"));
        set.add(model.getStandardPredicate("Prior"));

        Database resultsDB = dataStore.getDatabase(dataStore.getPartition(PARTITION_EVAL_TARGETS), set, dataStore.getPartition(PARTITION_EVAL_OBSERVATIONS));
        Database truthDB = dataStore.getDatabase(dataStore.getPartition(PARTITION_EVAL_TRUTH), dataStore.getRegisteredPredicates());

        Evaluator eval = new RankingEvaluator();
        eval.compute(resultsDB, truthDB,model.getStandardPredicate("Trusts"));
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

        try{
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
