package org.linqs.psl.examples.kgi;

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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class Run {
    private static final String PARTITION_LEARN_OBSERVATIONS = "learn_observations";
    private static final String PARTITION_LEARN_TARGETS = "learn_targets";
    private static final String PARTITION_LEARN_TRUTH = "learn_truth";

    private static final String PARTITION_EVAL_OBSERVATIONS = "eval_observations";
    private static final String PARTITION_EVAL_TARGETS = "eval_targets";
    private static final String PARTITION_EVAL_TRUTH = "eval_truth";

    private static final String DATA_PATH = Paths.get("..", "data", "kgi").toString();
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
        model.addPredicate("Sub", ConstantType.UniqueIntID, ConstantType.UniqueIntID);
        model.addPredicate("RSub", ConstantType.UniqueIntID, ConstantType.UniqueIntID);
        model.addPredicate("Mut", ConstantType.UniqueIntID, ConstantType.UniqueIntID);
        model.addPredicate("RMut", ConstantType.UniqueIntID, ConstantType.UniqueIntID);
        model.addPredicate("Inv", ConstantType.UniqueIntID, ConstantType.UniqueIntID);
        model.addPredicate("Domain", ConstantType.UniqueIntID, ConstantType.UniqueIntID);
        model.addPredicate("Range2", ConstantType.UniqueIntID, ConstantType.UniqueIntID);
        model.addPredicate("SameEntity", ConstantType.UniqueIntID, ConstantType.UniqueIntID);
        model.addPredicate("ValCat", ConstantType.UniqueIntID, ConstantType.UniqueIntID);
        model.addPredicate("ValRel", ConstantType.UniqueIntID, ConstantType.UniqueIntID, ConstantType.UniqueIntID);
        model.addPredicate("CandCat_General", ConstantType.UniqueIntID, ConstantType.UniqueIntID);
        model.addPredicate("CandRel_General", ConstantType.UniqueIntID, ConstantType.UniqueIntID, ConstantType.UniqueIntID);
        model.addPredicate("CandCat_CBL", ConstantType.UniqueIntID, ConstantType.UniqueIntID);
        model.addPredicate("CandRel_CBL", ConstantType.UniqueIntID, ConstantType.UniqueIntID, ConstantType.UniqueIntID);
        model.addPredicate("CandCat_CMC", ConstantType.UniqueIntID, ConstantType.UniqueIntID);
        model.addPredicate("CandCat_CPL", ConstantType.UniqueIntID, ConstantType.UniqueIntID);
        model.addPredicate("CandRel_CPL", ConstantType.UniqueIntID, ConstantType.UniqueIntID, ConstantType.UniqueIntID);
        model.addPredicate("CandCat_Morph", ConstantType.UniqueIntID, ConstantType.UniqueIntID);
        model.addPredicate("CandCat_SEAL", ConstantType.UniqueIntID, ConstantType.UniqueIntID);
        model.addPredicate("CandRel_SEAL", ConstantType.UniqueIntID, ConstantType.UniqueIntID, ConstantType.UniqueIntID);
        model.addPredicate("PromCat_General", ConstantType.UniqueIntID, ConstantType.UniqueIntID);
        model.addPredicate("PromRel_General", ConstantType.UniqueIntID, ConstantType.UniqueIntID, ConstantType.UniqueIntID);
        model.addPredicate("Cat", ConstantType.UniqueIntID, ConstantType.UniqueIntID);
        model.addPredicate("Rel", ConstantType.UniqueIntID, ConstantType.UniqueIntID, ConstantType.UniqueIntID);
    }

    /**
     * Defines the rules for this model.
     */
    private void defineRules() {
        log.info("Defining model rules");

           model.addRules(
                   "0025 : VALCAT(B, C) & SAMEENTITY(A, B) & CAT(A, C) -> CAT(B, C) ^2" +
                   "0025: VALREL(B, Z, R) & SAMEENTITY(A, B) & REL(A, Z, R) -> REL(B, Z, R) ^2" +
                   "0025: VALREL(Z, B, R) & SAMEENTITY(A, B) & REL(Z, A, R) -> REL(Z, B, R) ^2" +
                   "0100: VALCAT(A, D) & SUB(C, D) & CAT(A, C) -> CAT(A, D) ^2" +
                   "0100: VALREL(A, B, S) & RSUB(R, S) & REL(A, B, R) -> REL(A, B, S) ^2" +
                   "0100: VALCAT(A, D) & MUT(C, D) & CAT(A, C) -> !CAT(A, D) ^2" +
                   "0100: VALREL(A, B, S) & RMUT(R, S) & REL(A, B, R) -> !REL(A, B, S) ^2" +
                   "0100: VALREL(B, A, S) & INV(R, S) & REL(A, B, R) -> REL(B, A, S) ^2" +
                   "0100: VALCAT(A, C) & DOMAIN(R, C) & REL(A, B, R) -> CAT(A, C) ^2" +
                   "0100: VALCAT(B, C) & RANGE2(R, C) & REL(A, B, R) -> CAT(B, C) ^2" +
                   "0001: VALCAT(A, C) & CANDCAT_GENERAL(A, C) -> CAT(A, C) ^2" +
                   "0001: VALREL(A, B, R) & CANDREL_GENERAL(A, B, R) -> REL(A, B, R) ^2" +
                   "0001: VALCAT(A, C) & CANDCAT_CBL(A, C) -> CAT(A, C) ^2" +
                   "0001: VALREL(A, B, R) & CANDREL_CBL(A, B, R) -> REL(A, B, R) ^2" +
                   "0001: VALCAT(A, C) & CANDCAT_CMC(A, C) -> CAT(A, C) ^2" +
                   "0001: VALCAT(A, C) & CANDCAT_CPL(A, C) -> CAT(A, C) ^2" +
                   "0001: VALREL(A, B, R) & CANDREL_CPL(A, B, R) -> REL(A, B, R) ^2" +
                   "0001: VALCAT(A, C) & CANDCAT_MORPH(A, C) -> CAT(A, C) ^2" +
                   "0001: VALCAT(A, C) & CANDCAT_SEAL(A, C) -> CAT(A, C) ^2" +
                   "0001: VALREL(A, B, R) & CANDREL_SEAL(A, B, R) -> REL(A, B, R) ^2" +
                   "0001: VALCAT(A, C) & PROMCAT_GENERAL(A, C) -> CAT(A, C) ^2" +
                   "0001: VALREL(A, B, R) & PROMREL_GENERAL(A, B, R) -> REL(A, B, R) ^2");

           // Priors
           model.addRules(
                   "0002: VALCAT(A, C) -> !CAT(A, C) ^2" +
                   "0002: VALREL(A, B, R) -> !REL(A, B, R) ^2" +
                   "0001: VALCAT(A, C) -> CAT(A, C) ^2" +
                   "0001: VALREL(A, B, R) -> REL(A, B, R) ^2");

           log.debug("model: {}", model);
    }

    /**
     * Load data from text files into the DataStore.
     * Three partitions are defined and populated: observations, targets, and truth.
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

            for (StandardPredicate predicate : dataStore.getRegisteredPredicates()) {
                String path = Paths.get(DATA_PATH, split, predicate.getName() + "_obs.txt").toString();
                Inserter inserter = dataStore.getInserter(predicate, obsPartition);
                inserter.loadDelimitedDataTruth(path);
            }

            Inserter inserter = dataStore.getInserter(model.getStandardPredicate("Cat"), targetsPartition);
            inserter.loadDelimitedData(Paths.get(DATA_PATH, split, "CAT_targets.txt").toString());

            inserter = dataStore.getInserter(model.getStandardPredicate("Rel"), targetsPartition);
            inserter.loadDelimitedData(Paths.get(DATA_PATH, split, "REL_targets.txt").toString());

            inserter = dataStore.getInserter(model.getStandardPredicate("Cat"), truthPartition);
            inserter.loadDelimitedDataTruth(Paths.get(DATA_PATH, split, "CAT_truth.txt").toString());

            inserter = dataStore.getInserter(model.getStandardPredicate("Rel"), truthPartition);
            inserter.loadDelimitedDataTruth(Paths.get(DATA_PATH, split, "REL_truth.txt").toString());
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

        // Get all the predicates from the data store, and remove the open ones.
        Set<StandardPredicate> closedPredicates = dataStore.getRegisteredPredicates();
        closedPredicates.remove(model.getStandardPredicate("Cat"));
        closedPredicates.remove(model.getStandardPredicate("Rel"));

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

        // Get all the predicates from the data store, and remove the open ones.
        Set<StandardPredicate> closedPredicates = dataStore.getRegisteredPredicates();
        closedPredicates.remove(model.getStandardPredicate("Cat"));
        closedPredicates.remove(model.getStandardPredicate("Rel"));

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
    private void writeOutput() throws IOException {
        (new File(OUTPUT_PATH)).mkdirs();

        Database resultsDB = dataStore.getDatabase(dataStore.getPartition(PARTITION_EVAL_TARGETS));

        ArrayList<StandardPredicate>list = new ArrayList<StandardPredicate>();
        list.add(model.getStandardPredicate("Cat"));
        list.add(model.getStandardPredicate("Rel"));

        for (StandardPredicate predicate : list) {
            FileWriter writer = new FileWriter(Paths.get(OUTPUT_PATH, predicate.getName() + ".txt").toString());
            for (GroundAtom atom : resultsDB.getAllGroundAtoms(predicate)) {
                for (Constant argument : atom.getArguments()) {
                    writer.write(argument.toString() + "\t");
                }
                writer.write("" + atom.getValue() + "\n");
            }
            writer.close();
        }

        resultsDB.close();
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
        // Get all the predicates from the data store, and remove the open ones.
        Set<StandardPredicate> closedPredicates = dataStore.getRegisteredPredicates();
        closedPredicates.remove(model.getStandardPredicate("Cat"));
        closedPredicates.remove(model.getStandardPredicate("Rel"));

        Database resultsDB = dataStore.getDatabase(dataStore.getPartition(PARTITION_EVAL_TARGETS), closedPredicates, dataStore.getPartition(PARTITION_EVAL_OBSERVATIONS));
        Database truthDB = dataStore.getDatabase(dataStore.getPartition(PARTITION_EVAL_TRUTH), dataStore.getRegisteredPredicates());
        Evaluator eval = new RankingEvaluator();

        ArrayList<StandardPredicate>list = new ArrayList<StandardPredicate>();
        list.add(model.getStandardPredicate("Cat"));
        list.add(model.getStandardPredicate("Rel"));

        for (StandardPredicate predicate : list) {
            eval.compute(resultsDB, truthDB, predicate);
            log.info(eval.getAllStats());
        }

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
        } catch (IOException ex){
            throw new RuntimeException("Unable to write out Results.", ex);
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
