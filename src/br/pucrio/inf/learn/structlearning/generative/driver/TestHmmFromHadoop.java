package br.pucrio.inf.learn.structlearning.generative.driver;

import java.util.Map;

import br.pucrio.inf.learn.structlearning.generative.core.HmmModel;
import br.pucrio.inf.learn.structlearning.generative.data.Corpus;
import br.pucrio.inf.learn.structlearning.generative.evaluation.Evaluation;
import br.pucrio.inf.learn.structlearning.generative.evaluation.Performance;

/**
 * Evaluate a saved HMM (generated by a Hadoop-version algorithm) on the data
 * within a given file. Write the results in terms of precision, recall and F-1;
 * and also the number of entities, the number of predicted entities and the
 * number of correct predicted entities.
 * 
 * @author eraldof
 * 
 */
public class TestHmmFromHadoop {

	public static void main(String[] args) throws Exception {

		if (args.length != 4) {
			System.err
					.print("Syntax error: more arguments are necessary. Correct syntax:\n"
							+ "	<testfile> <modelfile> <observation feature index> <golden state feature index>\n");
			System.exit(1);
		}

		int arg = 0;
		String testFileName = args[arg++];
		String modelFileName = args[arg++];
		int observationFeature = Integer.parseInt(args[arg++]);
		int stateFeature = Integer.parseInt(args[arg++]);

		double smooth = 10e-6;

		System.out.println(String.format(
				"Evaluating HMM with the following parameters: \n"
						+ "\tTest file: %s\n" + "\tModel file: %s\n"
						+ "\tObservation feature: %d\n"
						+ "\tState feature: %d\n" + "\tSmoothing: %e\n",
				testFileName, modelFileName, observationFeature, stateFeature,
				smooth));

		// Load the model.
		HmmModel model = new HmmModel(modelFileName, true);
		if (smooth > 0.0) {
			// TODO use new smoothing interface.
			// model.setEmissionSmoothingProbability(smooth);
			model.normalizeProbabilities();
			model.applyLog();
		}

		// Load the testset.
		Corpus testset = new Corpus(testFileName,
				model.getFeatureValueEncoding(), true);

		// Test the model on a testset.
		model.setUseFinalProbabilities(false);
		model.tag(testset, observationFeature, -1);

		// Evaluate the predicted values.
		Evaluation ev = new Evaluation("0");
		Map<String, Performance> results = ev.evaluateSequences(testset,
				stateFeature, testset.getNumberOfFeatures() - 1);

		String[] labelOrder = { "LOC", "MISC", "ORG", "PER", "overall" };

		// Write precision, recall and F-1 values.
		System.out.println();
		System.out.println("|  *Class*  |  *P*  |  *R*  |  *F*  |");
		for (String label : labelOrder) {
			Performance res = results.get(label);
			if (res == null)
				continue;
			System.out.println(String.format(
					"|  %s  |  %6.2f |  %6.2f |  %6.2f |", label,
					100 * res.getPrecision(), 100 * res.getRecall(),
					100 * res.getF1()));
		}

		// // Write number of entities: total, predicted and correct.
		// System.out.println();
		// System.out.println("^  class  ^ Total ^ Retrieved ^ Correct ^");
		// for (String label : labelOrder) {
		// Verbose_res res = results.get(label);
		// if (res == null)
		// continue;
		// System.out.println(String.format("| %7s | %5d |  %8d | %7d |",
		// label, res.nobjects, res.nanswers, res.nfullycorrect));
		// }
	}
}