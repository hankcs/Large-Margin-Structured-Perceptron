package br.pucrio.inf.learn.structlearning.discriminative.application.pq;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import sun.reflect.generics.reflectiveObjects.NotImplementedException;
import br.pucrio.inf.learn.structlearning.discriminative.application.pq.data.PQInput2;
import br.pucrio.inf.learn.structlearning.discriminative.application.pq.data.PQOutput2;
import br.pucrio.inf.learn.structlearning.discriminative.application.sequence.AveragedParameter;
import br.pucrio.inf.learn.structlearning.discriminative.data.Dataset;
import br.pucrio.inf.learn.structlearning.discriminative.data.ExampleInput;
import br.pucrio.inf.learn.structlearning.discriminative.data.ExampleOutput;
import br.pucrio.inf.learn.structlearning.discriminative.task.Model;

/**
 * Person-quotation model. Just an array of weights (one for each feature).
 * 
 * @author eraldo
 * 
 */
public class PQModel2 implements Model {

	/**
	 * Feature parameters.
	 */
	private AveragedParameter[] featureWeights;

	/**
	 * Store the parameters updated in each training iteration.
	 */
	private Set<AveragedParameter> updatedParameters;

	public PQModel2(int numberOfFeatures) {
		featureWeights = new AveragedParameter[numberOfFeatures];
		for (int i = 0; i < featureWeights.length; ++i) {
			featureWeights[i] = new AveragedParameter();
		}

		updatedParameters = new HashSet<AveragedParameter>();
	}

	protected PQModel2(AveragedParameter[] featureWeights) {
		this.featureWeights = featureWeights;
		this.updatedParameters = new HashSet<AveragedParameter>();
	}

	/**
	 * Update the parameters of the features that differ from the two given
	 * output persons and that are present in the given input sequence.
	 * 
	 * @param input
	 * @param outputCorrect
	 * @param outputPredicted
	 * @param learningRate
	 * @return the loss between the correct and the predicted output.
	 */
	public double update(PQInput2 input, PQOutput2 outputCorrect,
			PQOutput2 outputPredicted, double learningRate) {
		int outputCorrectSize = outputCorrect.size();
		int numberOfErrors = 0;
		for (int i = 0; i < outputCorrectSize; ++i) {
			int labelCorrect = outputCorrect.getAuthor(i);
			int labelPredicted = outputPredicted.getAuthor(i);

			if (labelCorrect != labelPredicted) {
				++numberOfErrors;
				int featureIndex;

				/*
				 * This condition treats cases where the correct label is not
				 * available, because we removed some coreferences to make the
				 * classification task easier. Used by WIS
				 */
				// if(labelCorrect >= input.getNumberOfCoreferences(i))
				// continue;

				if (labelCorrect >= 0) {
					Iterator<Integer> it = input.getFeatureCodes(i,
							labelCorrect).iterator();
					while (it.hasNext()) {
						featureIndex = it.next();
						this.featureWeights[featureIndex].update(learningRate);
						updatedParameters.add(featureWeights[featureIndex]);
					}
				}

				if (labelPredicted >= 0) {
					Iterator<Integer> it = input.getFeatureCodes(i,
							labelPredicted).iterator();
					while (it.hasNext()) {
						featureIndex = it.next();
						this.featureWeights[featureIndex].update(-learningRate);
						updatedParameters.add(featureWeights[featureIndex]);
					}
				}
			}
		}

		return numberOfErrors;
	}

	public double update(ExampleInput input, ExampleOutput outputCorrect,
			ExampleOutput outputPredicted, double learningRate) {
		return update((PQInput2) input, (PQOutput2) outputCorrect,
				(PQOutput2) outputPredicted, learningRate);
	}

	@Override
	public void sumUpdates(int iteration) {
		for (AveragedParameter parm : updatedParameters)
			parm.sum(iteration);
		updatedParameters.clear();
	}

	@Override
	public void average(int numberOfIterations) {
		for (AveragedParameter parm : updatedParameters)
			parm.average(numberOfIterations);
	}

	/**
	 * Return the weight for the given feature code.
	 * 
	 * @param featureIndex
	 * @return
	 */
	public double getFeatureWeight(int featureIndex) {
		return this.featureWeights[featureIndex].get();
	}

	@Override
	public PQModel2 clone() throws CloneNotSupportedException {
		AveragedParameter[] clones = new AveragedParameter[featureWeights.length];
		for (int idx = 0; idx < clones.length; ++idx)
			clones[idx] = featureWeights[idx].clone();
		return new PQModel2(clones);
	}

	@Override
	public void save(String fileName, Dataset dataset) {
		throw new NotImplementedException();
	}

}
