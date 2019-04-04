package org.linqs.psl.examples.entityresolution;

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
import org.linqs.psl.evaluation.statistics.DiscreteEvaluator;
import org.linqs.psl.evaluation.statistics.Evaluator;
import org.linqs.psl.java.PSLModel;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.model.term.ConstantType;
import org.linqs.psl.utils.textsimilarity.DiceSimilarity;
import org.linqs.psl.utils.textsimilarity.LevenshteinSimilarity;
import org.linqs.psl.utils.textsimilarity.SameInitials;
import org.linqs.psl.utils.textsimilarity.SameNumTokens;
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

    private static final String DATA_PATH = Paths.get("..", "data", "entity-resolution").toString();
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

        model = new PSLModel( dataStore);
    }

    /**
     * Defines the logical predicates used in this model.
     */
    private void definePredicates() {
        model.addPredicate("AuthorName", ConstantType.UniqueIntID, ConstantType.String);
        model.addPredicate("PaperTitle", ConstantType.UniqueIntID, ConstantType.String);
        model.addPredicate("AuthorOf", ConstantType.UniqueIntID, ConstantType.UniqueIntID);
        model.addFunction("SimName", new LevenshteinSimilarity(0.5));
        model.addFunction("SimTitle", new DiceSimilarity(0.5));
        model.addFunction("SameInitials", new SameInitials());
        model.addFunction("SameNumTokens", new SameNumTokens());
        model.addPredicate("SameAuthor", ConstantType.UniqueIntID, ConstantType.UniqueIntID);
        model.addPredicate("SamePaper", ConstantType.UniqueIntID, ConstantType.UniqueIntID);
        model.addFunction("SameAuthorSet", new JaroWinklerSetSimilarity());
    }

    /**
     * Defines the rules for this model.
     */
    private void defineRules() {
        log.info("Defining model rules");

        // Similar names implies the same author
        model.addRule("1.0: AuthorName(A1,N1) & AuthorName(A2,N2) & SimName(N1,N2) >> SameAuthor(A1,A2)");

        // Similar titles implies the same paper
        model.addRule("1.0: PaperTitle(P1,T1) & PaperTitle(P2,T2) & SimTitle(T1,T2) >> SamePaper(P1,P2)");

        // If two references share a common publication and have the same initials, then this implies the same author
        model.addRule("1.0: AuthorOf(A1,P1) & AuthorOf(A2,P2) & SamePaper(P1,P2) & AuthorName(A1,N1) & AuthorName(A2,N2) & SameInitials(N1,N2) >> SameAuthor(A1,A2)");

        // If two papers have a common set of authors and the same number of tokens in the title, then this implies the same paper
        model.addRule("1.0: SameAuthorSet(P1,P2) & PaperTitle(P1,T1) & PaperTitle(P2,T2) & SameNumTokens(T1,T2) >> SamePaper(P1,P2)");

        // Priors: By default, Authors are not the same Authors and Papers are not the same Papers
        model.addRule("1.0: !SameAuthor(A1,A2)");

        model.addRule("1.0: !SamePaper(A1,A2)");

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

        String splits[] = new String[]{"eval","learn"};
        for (String split : splits) {
            Partition obsPartition = dataStore.getPartition(split + "_observations");
            Partition targetsPartition = dataStore.getPartition(split + "_targets");
            Partition truthPartition = dataStore.getPartition(split + "_truth");

            Inserter inserter = dataStore.getInserter(model.getStandardPredicate("AuthorName"), obsPartition);
            inserter.loadDelimitedDataAutomatic(Paths.get(DATA_PATH, split, "authorName_obs.txt").toString());

            inserter = dataStore.getInserter(model.getStandardPredicate("AuthorOf"), obsPartition);
            inserter.loadDelimitedDataAutomatic(Paths.get(DATA_PATH, split, "authorOf_obs.txt").toString());

            inserter = dataStore.getInserter(model.getStandardPredicate("PaperTitle"), obsPartition);
            inserter.loadDelimitedDataAutomatic(Paths.get(DATA_PATH, split, "paperTitle_obs.txt").toString());

            inserter = dataStore.getInserter(model.getStandardPredicate("SameAuthor"), targetsPartition);
            inserter.loadDelimitedDataAutomatic(Paths.get(DATA_PATH, split, "sameAuthor_targets.txt").toString());

            inserter = dataStore.getInserter(model.getStandardPredicate("SameAuthor"), truthPartition);
            inserter.loadDelimitedDataAutomatic(Paths.get(DATA_PATH, split, "sameAuthor_truth.txt").toString());

            inserter = dataStore.getInserter(model.getStandardPredicate("SamePaper"), targetsPartition);
            inserter.loadDelimitedDataAutomatic(Paths.get(DATA_PATH, split, "samePaper_targets.txt").toString());

            inserter = dataStore.getInserter(model.getStandardPredicate("SamePaper"), truthPartition);
            inserter.loadDelimitedDataAutomatic(Paths.get(DATA_PATH, split, "samePaper_truth.txt").toString());
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

        Set<StandardPredicate> closedPredicates = new HashSet<StandardPredicate>();
        closedPredicates.add(model.getStandardPredicate("AuthorName"));
        closedPredicates.add(model.getStandardPredicate("AuthorOf"));
        closedPredicates.add(model.getStandardPredicate("PaperTitle"));

        // This database contains all the ground atoms (targets) that we want to infer.
        // It also includes the observed data (because we will run inference over this db).
        Database randomVariableDatabase = dataStore.getDatabase(targetsPartition, closedPredicates, obsPartition);

        // This database only contains the true ground atoms.
        Database observedTruthDatabase = dataStore.getDatabase(truthPartition, dataStore.getRegisteredPredicates());

        WeightLearningApplication weightLearning = new MaxLikelihoodMPE(model, randomVariableDatabase, observedTruthDatabase);
        weightLearning.learn();

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

        Set<StandardPredicate> closedPredicates = new HashSet<StandardPredicate>();
        closedPredicates.add(model.getStandardPredicate("AuthorName"));
        closedPredicates.add(model.getStandardPredicate("AuthorOf"));
        closedPredicates.add(model.getStandardPredicate("PaperTitle"));

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
        Database resultsDB = dataStore.getDatabase(dataStore.getPartition(PARTITION_EVAL_TARGETS));

        (new File(OUTPUT_PATH)).mkdirs();
        FileWriter writer = new FileWriter(Paths.get(OUTPUT_PATH, "SAMEAUTHOR.txt").toString());

        for (GroundAtom atom : resultsDB.getAllGroundAtoms(model.getStandardPredicate("SameAuthor"))) {
            for (Constant argument : atom.getArguments()) {
                writer.write(argument.toString() + "\t");
            }
            writer.write("" + atom.getValue() + "\n");
        }

        (new File(OUTPUT_PATH)).mkdirs();
        writer = new FileWriter(Paths.get(OUTPUT_PATH, "SAMEPAPER.txt").toString());

        for (GroundAtom atom : resultsDB.getAllGroundAtoms(model.getStandardPredicate("SamePaper"))) {
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
        Set<StandardPredicate> closedPredicates = new HashSet<StandardPredicate>();
        closedPredicates.add(model.getStandardPredicate("AuthorName"));
        closedPredicates.add(model.getStandardPredicate("AuthorOf"));
        closedPredicates.add(model.getStandardPredicate("PaperTitle"));

        Set<StandardPredicate> openPredicates = new HashSet<StandardPredicate>();
        openPredicates.add(model.getStandardPredicate("SameAuthor"));
        openPredicates.add(model.getStandardPredicate("SamePaper"));

        Partition targetPartition = dataStore.getPartition(PARTITION_EVAL_TARGETS);
        Partition observationsPartition = dataStore.getPartition(PARTITION_EVAL_OBSERVATIONS);
        Partition truthPartition = dataStore.getPartition(PARTITION_EVAL_TRUTH);

        // Because the truth data also includes observed data, we will make sure to include the observed
        // partition here.
        Database resultsDB = dataStore.getDatabase(targetPartition, closedPredicates, observationsPartition);
        Database truthDB = dataStore.getDatabase(truthPartition, dataStore.getRegisteredPredicates());

        Evaluator eval = new DiscreteEvaluator();

        for (StandardPredicate targetPredicate : openPredicates) {
            eval.compute(resultsDB, truthDB, targetPredicate);
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
