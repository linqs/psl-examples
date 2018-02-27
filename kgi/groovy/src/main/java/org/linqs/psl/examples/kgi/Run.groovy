package org.linqs.psl.examples.kgi;

import org.linqs.psl.application.inference.MPEInference;
import org.linqs.psl.application.learning.weight.VotedPerceptron;
import org.linqs.psl.application.learning.weight.maxlikelihood.MaxLikelihoodMPE;
import org.linqs.psl.config.ConfigBundle;
import org.linqs.psl.config.ConfigManager;
import org.linqs.psl.database.Database;
import org.linqs.psl.database.DataStore;
import org.linqs.psl.database.Partition;
import org.linqs.psl.database.Queries;
import org.linqs.psl.database.loading.Inserter;
import org.linqs.psl.database.rdbms.driver.H2DatabaseDriver;
import org.linqs.psl.database.rdbms.driver.H2DatabaseDriver.Type;
import org.linqs.psl.database.rdbms.driver.PostgreSQLDriver;
import org.linqs.psl.database.rdbms.RDBMSDataStore;
import org.linqs.psl.evaluation.statistics.Evaluator;
import org.linqs.psl.evaluation.statistics.RankingEvaluator;
import org.linqs.psl.groovy.PSLModel;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.predicate.StandardPredicate;
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

	private static final String DATA_PATH = Paths.get("..", "data", "kgi").toString();
	private static final String OUTPUT_PATH = "inferred-predicates";

	private static Logger log = LoggerFactory.getLogger(Run.class)

	private DataStore dataStore;
	private ConfigBundle config;
	private PSLModel model;

	public Run() {
		config = ConfigManager.getManager().getBundle("kgi");

		String suffix = System.getProperty("user.name") + "@" + getHostname();
		String baseDBPath = config.getString("dbpath", System.getProperty("java.io.tmpdir"));
		String dbPath = Paths.get(baseDBPath, this.getClass().getName() + "_" + suffix).toString();
		dataStore = new RDBMSDataStore(new H2DatabaseDriver(Type.Disk, dbPath, true), config);
		// dataStore = new RDBMSDataStore(new PostgreSQLDriver("psl", true), config);

		model = new PSLModel(this, dataStore);
	}

	/**
	 * Defines the logical predicates used in this model
	 */
	private void definePredicates() {
		model.add predicate: "Sub", types: [ConstantType.UniqueIntID, ConstantType.UniqueIntID];
		model.add predicate: "RSub", types: [ConstantType.UniqueIntID, ConstantType.UniqueIntID];
		model.add predicate: "Mut", types: [ConstantType.UniqueIntID, ConstantType.UniqueIntID];
		model.add predicate: "RMut", types: [ConstantType.UniqueIntID, ConstantType.UniqueIntID];
		model.add predicate: "Inv", types: [ConstantType.UniqueIntID, ConstantType.UniqueIntID];
		model.add predicate: "Domain", types: [ConstantType.UniqueIntID, ConstantType.UniqueIntID];
		model.add predicate: "Range2", types: [ConstantType.UniqueIntID, ConstantType.UniqueIntID];
		model.add predicate: "SameEntity", types: [ConstantType.UniqueIntID, ConstantType.UniqueIntID];
		model.add predicate: "ValCat", types: [ConstantType.UniqueIntID, ConstantType.UniqueIntID];
		model.add predicate: "ValRel", types: [ConstantType.UniqueIntID, ConstantType.UniqueIntID, ConstantType.UniqueIntID];
		model.add predicate: "CandCat_General", types: [ConstantType.UniqueIntID, ConstantType.UniqueIntID];
		model.add predicate: "CandRel_General", types: [ConstantType.UniqueIntID, ConstantType.UniqueIntID, ConstantType.UniqueIntID];
		model.add predicate: "CandCat_CBL", types: [ConstantType.UniqueIntID, ConstantType.UniqueIntID];
		model.add predicate: "CandRel_CBL", types: [ConstantType.UniqueIntID, ConstantType.UniqueIntID, ConstantType.UniqueIntID];
		model.add predicate: "CandCat_CMC", types: [ConstantType.UniqueIntID, ConstantType.UniqueIntID];
		model.add predicate: "CandCat_CPL", types: [ConstantType.UniqueIntID, ConstantType.UniqueIntID];
		model.add predicate: "CandRel_CPL", types: [ConstantType.UniqueIntID, ConstantType.UniqueIntID, ConstantType.UniqueIntID];
		model.add predicate: "CandCat_Morph", types: [ConstantType.UniqueIntID, ConstantType.UniqueIntID];
		model.add predicate: "CandCat_SEAL", types: [ConstantType.UniqueIntID, ConstantType.UniqueIntID];
		model.add predicate: "CandRel_SEAL", types: [ConstantType.UniqueIntID, ConstantType.UniqueIntID, ConstantType.UniqueIntID];
		model.add predicate: "PromCat_General", types: [ConstantType.UniqueIntID, ConstantType.UniqueIntID];
		model.add predicate: "PromRel_General", types: [ConstantType.UniqueIntID, ConstantType.UniqueIntID, ConstantType.UniqueIntID];
		model.add predicate: "Cat", types: [ConstantType.UniqueIntID, ConstantType.UniqueIntID];
		model.add predicate: "Rel", types: [ConstantType.UniqueIntID, ConstantType.UniqueIntID, ConstantType.UniqueIntID];
	}

	/**
	 * Defines the rules for this model.
	 */
	private void defineRules() {
		log.info("Defining model rules");

		model.addRules("""
			0025: VALCAT(B, C) & SAMEENTITY(A, B) & CAT(A, C) -> CAT(B, C) ^2
			0025: VALREL(B, Z, R) & SAMEENTITY(A, B) & REL(A, Z, R) -> REL(B, Z, R) ^2
			0025: VALREL(Z, B, R) & SAMEENTITY(A, B) & REL(Z, A, R) -> REL(Z, B, R) ^2
			0100: VALCAT(A, D) & SUB(C, D) & CAT(A, C) -> CAT(A, D) ^2
			0100: VALREL(A, B, S) & RSUB(R, S) & REL(A, B, R) -> REL(A, B, S) ^2
			0100: VALCAT(A, D) & MUT(C, D) & CAT(A, C) -> !CAT(A, D) ^2
			0100: VALREL(A, B, S) & RMUT(R, S) & REL(A, B, R) -> !REL(A, B, S) ^2
			0100: VALREL(B, A, S) & INV(R, S) & REL(A, B, R) -> REL(B, A, S) ^2
			0100: VALCAT(A, C) & DOMAIN(R, C) & REL(A, B, R) -> CAT(A, C) ^2
			0100: VALCAT(B, C) & RANGE2(R, C) & REL(A, B, R) -> CAT(B, C) ^2
			0001: VALCAT(A, C) & CANDCAT_GENERAL(A, C) -> CAT(A, C) ^2
			0001: VALREL(A, B, R) & CANDREL_GENERAL(A, B, R) -> REL(A, B, R) ^2
			0001: VALCAT(A, C) & CANDCAT_CBL(A, C) -> CAT(A, C) ^2
			0001: VALREL(A, B, R) & CANDREL_CBL(A, B, R) -> REL(A, B, R) ^2
			0001: VALCAT(A, C) & CANDCAT_CMC(A, C) -> CAT(A, C) ^2
			0001: VALCAT(A, C) & CANDCAT_CPL(A, C) -> CAT(A, C) ^2
			0001: VALREL(A, B, R) & CANDREL_CPL(A, B, R) -> REL(A, B, R) ^2
			0001: VALCAT(A, C) & CANDCAT_MORPH(A, C) -> CAT(A, C) ^2
			0001: VALCAT(A, C) & CANDCAT_SEAL(A, C) -> CAT(A, C) ^2
			0001: VALREL(A, B, R) & CANDREL_SEAL(A, B, R) -> REL(A, B, R) ^2
			0001: VALCAT(A, C) & PROMCAT_GENERAL(A, C) -> CAT(A, C) ^2
			0001: VALREL(A, B, R) & PROMREL_GENERAL(A, B, R) -> REL(A, B, R) ^2

			// Priors
			0002: VALCAT(A, C) -> !CAT(A, C) ^2
			0002: VALREL(A, B, R) -> !REL(A, B, R) ^2
			0001: VALCAT(A, C) -> CAT(A, C) ^2
			0001: VALREL(A, B, R) -> REL(A, B, R) ^2
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

			for (StandardPredicate predicate : dataStore.getRegisteredPredicates()) {
				String path = Paths.get(DATA_PATH, type, predicate.getName() + "_obs.txt").toString();
				Inserter inserter = dataStore.getInserter(predicate, obsPartition);
				inserter.loadDelimitedDataTruth(path);
			}

			Inserter inserter = dataStore.getInserter(Cat, targetsPartition);
			inserter.loadDelimitedData(Paths.get(DATA_PATH, type, "CAT_targets.txt").toString());

			inserter = dataStore.getInserter(Rel, targetsPartition);
			inserter.loadDelimitedData(Paths.get(DATA_PATH, type, "REL_targets.txt").toString());

			inserter = dataStore.getInserter(Cat, truthPartition);
			inserter.loadDelimitedDataTruth(Paths.get(DATA_PATH, type, "CAT_truth.txt").toString());

			inserter = dataStore.getInserter(Rel, truthPartition);
			inserter.loadDelimitedDataTruth(Paths.get(DATA_PATH, type, "REL_truth.txt").toString());
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

		Set<StandardPredicate> closedPredicates = [
			Sub, RSub, Mut, RMut, Inv, Domain, Range2, SameEntity, ValCat, ValRel,
			CandCat_General, CandRel_General, CandCat_CBL, CandRel_CBL, CandCat_CMC,
			CandCat_CPL, CandRel_CPL, CandCat_Morph, CandCat_SEAL, CandRel_SEAL,
			PromCat_General, PromRel_General
		] as Set;

		// This database contains all the ground atoms (targets) that we want to infer.
		// It also includes the observed data (because we will run inference over this db).
		Database randomVariableDatabase = dataStore.getDatabase(targetsPartition, closedPredicates, obsPartition);

		// This database only contains the true ground atoms.
		Database observedTruthDatabase = dataStore.getDatabase(truthPartition, dataStore.getRegisteredPredicates());

		VotedPerceptron vp = new MaxLikelihoodMPE(model, randomVariableDatabase, observedTruthDatabase, config);
		vp.learn();

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

		Set<StandardPredicate> closedPredicates = [
			Sub, RSub, Mut, RMut, Inv, Domain, Range2, SameEntity, ValCat, ValRel,
			CandCat_General, CandRel_General, CandCat_CBL, CandRel_CBL, CandCat_CMC,
			CandCat_CPL, CandRel_CPL, CandCat_Morph, CandCat_SEAL, CandRel_SEAL,
			PromCat_General, PromRel_General
		] as Set;

		Database inferDB = dataStore.getDatabase(targetsPartition, closedPredicates, obsPartition);

		MPEInference mpe = new MPEInference(model, inferDB, config);
		mpe.mpeInference();

		mpe.close();
		inferDB.close();

		log.info("Inference complete");
	}

	/**
	 * Writes the output of the model into a file
	 */
	private void writeOutput() {
		(new File(OUTPUT_PATH)).mkdirs();

		Database resultsDB = dataStore.getDatabase(dataStore.getPartition(PARTITION_EVAL_TARGETS));

		for (StandardPredicate predicate : [Cat, Rel]) {
			FileWriter writer = new FileWriter(Paths.get(OUTPUT_PATH, predicate.getName() + ".txt").toString());

			for (GroundAtom atom : Queries.getAllAtoms(resultsDB, predicate)) {
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
		Set<StandardPredicate> closedPredicates = [
			Sub, RSub, Mut, RMut, Inv, Domain, Range2, SameEntity, ValCat, ValRel,
			CandCat_General, CandRel_General, CandCat_CBL, CandRel_CBL, CandCat_CMC,
			CandCat_CPL, CandRel_CPL, CandCat_Morph, CandCat_SEAL, CandRel_SEAL,
			PromCat_General, PromRel_General
		] as Set;

		// Because the truth data also includes observed data, we will make sure to include the observed
		// partition here.
		Database resultsDB = dataStore.getDatabase(dataStore.getPartition(PARTITION_EVAL_TARGETS),
				closedPredicates, dataStore.getPartition(PARTITION_EVAL_OBSERVATIONS));
		Database truthDB = dataStore.getDatabase(dataStore.getPartition(PARTITION_EVAL_TRUTH),
				dataStore.getRegisteredPredicates());

		Evaluator eval = new RankingEvaluator();

		for (StandardPredicate predicate : [Cat, Rel]) {
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
