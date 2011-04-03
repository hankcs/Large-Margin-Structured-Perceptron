package br.pucrio.inf.learn.structlearning.algorithm;

import br.pucrio.inf.learn.structlearning.application.sequence.SequenceInput;
import br.pucrio.inf.learn.structlearning.application.sequence.SequenceOutput;
import br.pucrio.inf.learn.structlearning.data.ExampleInput;
import br.pucrio.inf.learn.structlearning.data.ExampleOutput;
import br.pucrio.inf.learn.structlearning.task.Inference;
import br.pucrio.inf.learn.structlearning.task.Model;
import br.pucrio.inf.learn.util.DebugUtil;

/**
 * McAllester et al.'s Perceptron implementation that uses a modified updating
 * rule which is proved to directly optimize the used loss function.
 * 
 * The difference from the away-from-worse implementation (this one) to the
 * toward-better implementation is that the former uses a loss-augmented
 * inference that privileges high-loss solutions and updates the model weights
 * away from the loss-augmented solution that is worse than the
 * non-loss-augmented solution.
 * 
 * On the other hand, the toward-better implementation privileges low-loss
 * solutions and updates the model weights toward this better solution.
 * 
 * @author eraldof
 * 
 */
public class AwayFromWorsePerceptron extends LossAugmentedPerceptron {

	public AwayFromWorsePerceptron(Inference taskImpl, Model initialModel) {
		super(taskImpl, initialModel);
	}

	public AwayFromWorsePerceptron(Inference taskImpl, Model initialModel,
			int numberOfIterations, double learningRate, double lossWeight) {
		super(taskImpl, initialModel, numberOfIterations, learningRate,
				lossWeight);
	}

	@Override
	public double trainOneExample(ExampleInput input,
			ExampleOutput correctOutput, ExampleOutput predictedOutput) {

		ExampleOutput referenceOutput = correctOutput;
		if (partiallyAnnotatedExamples) {
			// If the user asked to consider partially-labeled examples then
			// infer the missing values within the given correct output
			// structure before updating the current model.
			referenceOutput = correctOutput.createNewObject();
			inferenceImpl.partialInference(model, input, correctOutput,
					referenceOutput);
		}

		// Infer the whole output structure using the loss function. This is
		// the "worse" output structure used to update the model.
		ExampleOutput lossAugmentedPredictedOutput = correctOutput
				.createNewObject();
		inferenceImpl.lossAugmentedInference(model, input, referenceOutput,
				lossAugmentedPredictedOutput, lossWeight);

		// Infer the whole output structure. This is the "better" output
		// structure used to update the model.
		inferenceImpl.inference(model, input, predictedOutput);

		// Update the current model and return the loss for this example.
		double loss = model.update(input, predictedOutput,
				lossAugmentedPredictedOutput, learningRate);

		// TODO debug
		if (DebugUtil.print && loss != 0d)
			DebugUtil.printSequence((SequenceInput) input,
					(SequenceOutput) predictedOutput,
					(SequenceOutput) lossAugmentedPredictedOutput, loss);

		// Averaged-Perceptron: account the updates into the averaged
		// weights.
		model.sumUpdates(iteration);

		return loss;

	}

}
