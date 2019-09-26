package org.linqs.psl.examples.citationcategories;

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
import org.linqs.psl.evaluation.statistics.CategoricalEvaluator;
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
import java.util.HashSet;
import java.util.Set;

public class Run {
    private static final String PARTITION_LEARN_OBSERVATIONS = "learn_observations";
    private static final String PARTITION_LEARN_TARGETS = "learn_targets";
    private static final String PARTITION_LEARN_TRUTH = "learn_truth";

    private static final String PARTITION_EVAL_OBSERVATIONS = "eval_observations";
    private static final String PARTITION_EVAL_TARGETS = "eval_targets";
    private static final String PARTITION_EVAL_TRUTH = "eval_truth";

    private static final String DATA_PATH = Paths.get("..", "data", "citation-categories").toString();
    private static final String OUTPUT_PATH = "inferred-predicates";
    private static final int NUM_CATEGORIES = 7;

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
        model.addPredicate("Link", ConstantType.UniqueIntID, ConstantType.UniqueIntID);
        model.addPredicate("HasCat", ConstantType.UniqueIntID, ConstantType.UniqueIntID);
    }

    /**
     * Defines the rules for this model.
     */
    private void defineRules() {
        log.info("Defining model rules");

        model.addRule("1.0: HasCat(A, C) & Link(A, B) & (A != B) >> HasCat(B, C) ^2");

        model.addRule("1.0: HasCat(A, C) & Link(B, A) & (A != B) >> HasCat(B, C) ^2");

        // Add one rule for each category.
        for (int cat = 1; cat <= NUM_CATEGORIES; cat++) {
            String stringRule = String.format("1.0: HasCat(A, '%d') & Link(A, B) >> HasCat(B, '%d') ^2", cat, cat);
            model.addRule(stringRule);
        }

        model.addRule("HasCat(A, +C) = 1 .");

        model.addRule("0.001: !HasCat(A, N) ^2");

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

            Inserter inserter = dataStore.getInserter(model.getStandardPredicate("Link"), obsPartition);
            inserter.loadDelimitedDataTruth(Paths.get(DATA_PATH, split, "link_obs.txt").toString());

            inserter = dataStore.getInserter(model.getStandardPredicate("HasCat"), obsPartition);
            inserter.loadDelimitedDataTruth(Paths.get(DATA_PATH, split, "hasCat_obs.txt").toString());

            inserter = dataStore.getInserter(model.getStandardPredicate("HasCat"), targetsPartition);
            inserter.loadDelimitedData(Paths.get(DATA_PATH, split, "hasCat_targets.txt").toString());

            inserter = dataStore.getInserter(model.getStandardPredicate("HasCat"), truthPartition);
            inserter.loadDelimitedDataTruth(Paths.get(DATA_PATH, split, "hasCat_truth.txt").toString());
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
        Set<StandardPredicate>set = new HashSet<StandardPredicate>();
        set.add(model.getStandardPredicate("Link"));
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
        Set<StandardPredicate>set = new HashSet<StandardPredicate>();
        set.add(model.getStandardPredicate("Link"));
        Database inferDB = dataStore.getDatabase(targetsPartition, set, obsPartition);

        InferenceApplication inference = new MPEInference(model, inferDB);
        inference.inference();
        inference.close();
        inferDB.close();

        log.info("Inference complete");
    }

    /**
     * Run statistical evaluation scripts to determine the quality of the inferences
     * relative to the defined truth.
     * Note that the target predicate is categorical and we will assign the category with the
     * highest truth value as true and the rest false.
     */
    private void evalResults() {
        // Because the truth data also includes observed data, we will make sure to include the observed
        // partition here.
        Set<StandardPredicate> set = new HashSet<StandardPredicate>();
        set.add(model.getStandardPredicate("Link"));
        Database resultsDB = dataStore.getDatabase(dataStore.getPartition(PARTITION_EVAL_TARGETS), set, dataStore.getPartition(PARTITION_EVAL_OBSERVATIONS));
        Database truthDB = dataStore.getDatabase(dataStore.getPartition(PARTITION_EVAL_TRUTH), dataStore.getRegisteredPredicates());

        Evaluator eval = new CategoricalEvaluator(1);
        eval.compute(resultsDB, truthDB, model.getStandardPredicate("HasCat"));
        log.info(eval.getAllStats());

        resultsDB.close();
        truthDB.close();
    }

    /**
     * Writes the output of the model into a file.
     */
    private void writeOutput() throws IOException {
        Database resultsDB = dataStore.getDatabase(dataStore.getPartition(PARTITION_EVAL_TARGETS));

        (new File(OUTPUT_PATH)).mkdirs();
        FileWriter writer = new FileWriter(Paths.get(OUTPUT_PATH, "HASCAT.txt").toString());

        for (GroundAtom atom : resultsDB.getAllGroundAtoms(model.getStandardPredicate("HasCat"))) {
            for (Constant argument : atom.getArguments()) {
                writer.write(argument.toString() + "\t");
            }
            writer.write("" + atom.getValue() + "\n");
        }

        writer.close();
        resultsDB.close();
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
