package br.pucrio.inf.learn.structlearning.discriminative.application.pq.data;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.Collection;
import java.util.LinkedList;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import br.pucrio.inf.learn.structlearning.discriminative.data.DatasetException;
import br.pucrio.inf.learn.structlearning.discriminative.data.encoding.FeatureEncoding;
import br.pucrio.inf.learn.structlearning.discriminative.data.encoding.StringMapEncoding;


/**
 * Represent a text dataset.
 * 
 * Provide methods for manipulating a text dataset. Some operations are: create
 * new features, remove features, get feature values, get a complete example,
 * change feature values.
 * 
 * The feature values are stored as integer. We use a feature-value mapping to
 * encode the string values.
 * 
 */
public class PQDataset {

	private static final Log LOG = LogFactory.getLog(PQDataset.class);

	/**
	 * Indicate whether this is a training dataset or not.
	 */
	protected boolean training;

	/**
	 * Map string feature values to integer values (codes).
	 */
	protected FeatureEncoding<String> featureEncoding;

	/**
	 * Vector of the input-part of the examples.
	 */
	protected PQInput[] inputExamples;

	/**
	 * Vector of the output-part of the examples (correct predictions).
	 */
	protected PQOutput[] outputExamples;

	/**
	 * Default constructor.
	 */
	public PQDataset() {
		this(new StringMapEncoding());
	}

	/**
	 * Create a dataset using the given feature-value encodings. One
	 * can use this constructor to create a dataset compatible with a
	 * previous loaded model, for instance.
	 * 
	 * @param featureEncoding
	 */
	public PQDataset(FeatureEncoding<String> featureEncoding) {
		this.featureEncoding = featureEncoding;
		this.training = false;
	}

	public PQDataset(FeatureEncoding<String> featureEncoding,
			boolean training) {
		this.featureEncoding = featureEncoding;
		this.training = training;
	}

	/**
	 * Load the dataset from a file.
	 * 
	 * @param fileName
	 *            the name of a file to load the dataset.
	 * 
	 * @throws DatasetException
	 * @throws IOException
	 */
	public PQDataset(String fileName) throws IOException, DatasetException {
		this(new StringMapEncoding());
		load(fileName);
	}

	/**
	 * Load the dataset from a <code>InputStream</code>.
	 * 
	 * @param is
	 * @throws IOException
	 * @throws DatasetException
	 */
	public PQDataset(InputStream is) throws IOException, DatasetException {
		this(new StringMapEncoding());
		load(is);
	}

	/**
	 * Load the dataset from the given file and use the given feature-value
	 * encodings. One can use this constructor to load a dataset compatible
	 * with a previous loaded model, for instance.
	 * 
	 * @param fileName
	 *            name and path of a file.
	 * @param featureEncoding
	 *            use a determined feature values encoding.
	 * 
	 * @throws IOException
	 *             if occurs some problem reading the file.
	 * @throws DatasetException
	 *             if the file contains invalid data.
	 */
	public PQDataset(String fileName, FeatureEncoding<String> featureEncoding)
				throws IOException, DatasetException {
		this(featureEncoding);
		load(fileName);
	}

	public PQInput getPQInput(int index) {
		return inputExamples[index];
	}

	/**
	 * Return the number of examples within this dataset.
	 * 
	 * @return the number of examples within this dataset
	 */
	public int getNumberOfExamples() {
		return inputExamples.length;
	}

	public FeatureEncoding<String> getFeatureEncoding() {
		return featureEncoding;
	}

	public PQInput[] getInputs() {
		return inputExamples;
	}

	public PQOutput[] getOutputs() {
		return outputExamples;
	}

	/**
	 * Load a dataset from the given stream.
	 * 
	 * @param fileName
	 *            the name of a file where to read from the dataset.
	 * 
	 * @throws IOException
	 * @throws DatasetException
	 */
	public void load(String fileName) throws IOException, DatasetException {
		BufferedReader reader = new BufferedReader(new FileReader(fileName));
		load(reader);
		reader.close();
	}

	/**
	 * Load a dataset from the given stream.
	 * 
	 * @param is
	 *            an input stream from where the dataset is loaded.
	 * @throws DatasetException
	 * @throws IOException
	 */
	public void load(InputStream is) throws IOException, DatasetException {
		BufferedReader reader = new BufferedReader(new InputStreamReader(is));
		load(reader);
	}

	/**
	 * Load a dataset from the given buffered reader.
	 * 
	 * @param reader
	 * @throws IOException
	 * @throws DatasetException
	 */
	public void load(BufferedReader reader) throws IOException,
			DatasetException {
		LinkedList<PQInput> inputExamples = new LinkedList<PQInput>();
		LinkedList<PQOutput> outputExamples = new LinkedList<PQOutput>();

		// Parse each example.
		int numTotal = 0;
		int numAdded = 0;
		String buff;
		while ((buff = skipBlanksAndComments(reader)) != null) {
			if (parseExample(inputExamples, outputExamples, buff))
				++numAdded;
			++numTotal;
		}

		LOG.info("Skipped " + (numTotal - numAdded) + " examples of "
				+ numTotal + " (" + (numTotal - numAdded) * 100d / numTotal
				+ "%)");

		this.inputExamples = inputExamples.toArray(new PQInput[0]);
		this.outputExamples = outputExamples.toArray(new PQOutput[0]);
	}

	/**
	 * Add the examples in the given dataset to this dataset.
	 * 
	 * @param other
	 */
	public void add(PQDataset other) throws DatasetException {
		if (!featureEncoding.equals(other.featureEncoding))
			throw new DatasetException("Different encodings");

		// Alloc room to store both datasets (this one and the given one).
		PQInput[] newInputExamples = new PQInput[inputExamples.length
				+ other.inputExamples.length];
		PQOutput[] newOutputExamples = new PQOutput[outputExamples.length
				+ other.outputExamples.length];

		// Copy (only reference) the examples in this dataset to the new arrays.
		int idx = 0;
		for (; idx < inputExamples.length; ++idx) {
			newInputExamples[idx] = inputExamples[idx];
			newOutputExamples[idx] = outputExamples[idx];
		}

		// Copy (only reference) the examples in the given dataset to the new
		// arrays.
		for (int idxO = 0; idxO < other.inputExamples.length; ++idxO, ++idx) {
			newInputExamples[idx] = other.inputExamples[idxO];
			newOutputExamples[idx] = other.outputExamples[idxO];
		}

		// Adjust the pointers of this dataset to the new arrays.
		this.inputExamples = newInputExamples;
		this.outputExamples = newOutputExamples;
	}

	/**
	 * Skip blank lines and lines starting by the comment character #.
	 * 
	 * @param reader
	 * @return
	 * @throws IOException
	 */
	protected String skipBlanksAndComments(BufferedReader reader)
			throws IOException {
		String buff;
		while ((buff = reader.readLine()) != null) {
			// Skip empty lines.
			if (buff.trim().length() == 0)
				continue;
			// Skip comment lines.
			if (buff.startsWith("#"))
				continue;
			break;
		}
		return buff;
	}

	/**
	 * Parse the given string and load an example.
	 * 
	 * @param buff
	 *            a string that contains an example.
	 * 
	 * @return <code>true</code> if the given string is a valid example.
	 * 
	 * @throws DatasetException
	 *             if there is some format problem with the given string.
	 */
	public boolean parseExample(Collection<PQInput> exampleInputs,
			Collection<PQOutput> exampleOutputs, String buff)
			throws DatasetException {
		// Split tokens.
		String tokens[] = buff.split("\\t");
		if (tokens.length == 0)
			return false;

		// The first field has the segment id and the right coreference number.
		String firstField[] = tokens[0].split("[ ]");
		if (firstField.length != 2)
			return false;
		
		// The first subfield is the segment id.
		String docId = firstField[0];
		if (docId.trim().length() == 0)
			return false;
		
		// The second subfield is the right coreference.
		if (firstField[1].trim().length() == 0)
			return false;
		int rightCoref = Integer.parseInt(firstField[1]);

		LinkedList<LinkedList<Integer>> exampleInputAsList = new LinkedList<LinkedList<Integer>>();

		for (int idxTkn = 1; idxTkn < tokens.length; ++idxTkn) {
			String token = tokens[idxTkn];

			// Parse the token features.
			String[] features = token.split("[ ]");
			LinkedList<Integer> featureList = new LinkedList<Integer>();
			for (int idxFtr = 0; idxFtr < features.length - 1; ++idxFtr) {
				int code = featureEncoding.put(features[idxFtr]);
				if (code >= 0)
					featureList.add(code);
			}
			
			exampleInputAsList.add(featureList);
		}

		// Store the loaded example.
		if (training) {
			/*
			 * Training examples must store internally their indexes in the
			 * array of training examples.
			 */
			exampleInputs.add(new PQInput(docId, exampleInputs
					.size(), exampleInputAsList));
			exampleOutputs.add(new PQOutput(rightCoref));
		} else {
			exampleInputs.add(new PQInput(docId,
					exampleInputAsList));
			exampleOutputs.add(new PQOutput(rightCoref));
		}
		return true;
	}

	/**
	 * Save this dataset in the given file.
	 * 
	 * @param fileName
	 *            name of the file to save this dataset.
	 * 
	 * @throws IOException
	 *             if some problem occurs when opening or writing to the file.
	 */
	public void save(String fileName) throws IOException {
		PrintStream ps = new PrintStream(fileName);
		save(ps);
		ps.close();
	}

	/**
	 * Save the dataset to the given stream.
	 * 
	 * @param os
	 *            an output stream to where the dataset is saved.
	 */
	public void save(PrintStream ps) {
		for (int idxExample = 0; idxExample < getNumberOfExamples(); ++idxExample) {
			PQInput input = inputExamples[idxExample];
			PQOutput output = outputExamples[idxExample];

			// The sentence identifier string.
			ps.print(input.getId());

			for (int token = 0; token < input.size(); ++token) {
				// Tokens as separated.
				ps.print("\t");

				// Token features.
				for (int ftr : input.getFeatureCodes(token))
					ps.print(featureEncoding.getValueByCode(ftr) + " ");

				// Label of this token.
				ps.println(featureEncoding.getValueByCode(output
						.getPerson()));
			}

			// Next line for the next sequence.
			ps.println();
		}
	}

	/**
	 * Return the number of tokens within the given example index.
	 * 
	 * @param idxExample
	 *            index of an example.
	 * 
	 * @return the number of tokens within the given example index.
	 */
	public int getNumberOfTokens(int idxExample) {
		return inputExamples[idxExample].size();
	}

	/**
	 * Return the number of symbols in the dataset feature-value encoding
	 * object. In general, this corresponds to the total number of different
	 * symbols in the dataset, but can be a different number if the encoding was
	 * used by other code despite this dataset.
	 * 
	 * @return
	 */
	public int getNumberOfSymbols() {
		return featureEncoding.size();
	}

	/**
	 * Normalize all the input structures within this dataset to have the same
	 * given norm.
	 * 
	 * @param norm
	 */
	public void normalizeInputStructures(double norm) {
		for (PQInput in : inputExamples)
			in.normalize(norm);
	}

	/**
	 * Sort feature values of each token to speedup kernel functions
	 * computations.
	 */
	public void sortFeatureValues() {
		for (PQInput seq : inputExamples)
			seq.sortFeatures();
	}

}
