package br.pucrio.inf.learn.structlearning.discriminative.algorithm;

import br.pucrio.inf.learn.structlearning.discriminative.data.ExampleInput;
import br.pucrio.inf.learn.structlearning.discriminative.data.ExampleOutput;

/**
 * Interface for a structured, online learning algorithm.
 * 
 * @author eraldof
 * 
 */
public interface OnlineStructuredAlgorithm extends StructuredAlgorithm {

	/**
	 * Strategy to update the learning rate.
	 * 
	 * @author eraldof
	 * 
	 */
	public enum LearnRateUpdateStrategy {
		/**
		 * No update, i.e., constant learning rate.
		 */
		NONE,

		/**
		 * The learning rate is equal to n/t, where n is the initial learning
		 * rate and t is the current iteration (number of processed examples).
		 */
		LINEAR,

		/**
		 * The learning rate is equal to n/(t*t), where n is the initial
		 * learning rate and t is the current iteration (number of processed
		 * examples).
		 */
		QUADRATIC,

		/**
		 * The learning rate is equal to n/(sqrt(t)), where n is the initial
		 * learning rate and t is the current iteration (number of processed
		 * examples).
		 */
		SQUARE_ROOT
	}

	/**
	 * Update the currect model using the given correct output and the predicted
	 * output for this example. Attention: the given <code>predicted</code> is
	 * only a placeholder to store the predicted structure, i.e., the prediction
	 * will be done inside this method.
	 * 
	 * @param input
	 *            the input structure.
	 * @param output
	 *            the correct output structured.
	 * @param predicted
	 *            a place holder for the predicted structured.
	 * @return the loss function value for the given correct output and the
	 *         predicted output using the current weight vector (before the
	 *         possible update generated by the given example).
	 */
	public double train(ExampleInput input, ExampleOutput output,
			ExampleOutput predicted);

	/**
	 * Set the learning rate.
	 * 
	 * @param rate
	 */
	public void setLearningRate(double rate);

	/**
	 * Return the current iteration.
	 * 
	 * @return
	 */
	public int getIteration();

}