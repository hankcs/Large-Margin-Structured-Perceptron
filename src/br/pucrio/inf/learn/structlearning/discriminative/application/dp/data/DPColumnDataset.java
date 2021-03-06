package br.pucrio.inf.learn.structlearning.discriminative.application.dp.data;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sun.reflect.generics.reflectiveObjects.NotImplementedException;
import br.pucrio.inf.learn.structlearning.discriminative.application.dp.Feature;
import br.pucrio.inf.learn.structlearning.discriminative.application.dp.FeatureTemplate;
import br.pucrio.inf.learn.structlearning.discriminative.application.dp.InvertedIndex;
import br.pucrio.inf.learn.structlearning.discriminative.application.dp.SimpleFeatureTemplate;
import br.pucrio.inf.learn.structlearning.discriminative.data.DatasetException;
import br.pucrio.inf.learn.structlearning.discriminative.data.encoding.FeatureEncoding;
import br.pucrio.inf.learn.structlearning.discriminative.data.encoding.MapEncoding;
import br.pucrio.inf.learn.structlearning.discriminative.data.encoding.StringMapEncoding;

/**
 * Represent a dataset with dependency parsing examples. Each examples consists
 * of a sentence. Each edge within a sentence is represented by a fixed number
 * of features (column-based representation, also commonly used in CoNLL shared
 * tasks). This class is useful for template-based models.
 * 
 * In this representation, there are two types of features: basic and explicit.
 * Basic features come from dataset columns. Each column has a name and an
 * sequential id, and, for each example, there is a string value for each basic
 * feature (column). Explicit features are instantiated from templates. These
 * are the "real" features, that is, the features used in the model. Generally,
 * each template combines some basic features and generates an explicit feature
 * for each edge.
 * 
 * There are also two encodings. The first one encodes basic features textual
 * values into integer codes. The second one encodes explicit features (i.e.,
 * combined basic features by means of templates) values into integer codes.
 * These later codes are used by the model as parameter indexes, i.e., for each
 * index, the model includes a learned weight.
 * 
 * @author eraldo
 * 
 */
public class DPColumnDataset implements DPDataset {

	/**
	 * Logging object.
	 */
	private static Log LOG = LogFactory.getLog(DPColumnDataset.class);

	/**
	 * Regular expression pattern to parse spaces.
	 */
	protected final Pattern REGEX_SPACE = Pattern.compile("[ ]");

	/**
	 * Encoding for basic textual features (column-format features).
	 */
	protected FeatureEncoding<String> basicEncoding;

	/**
	 * Template set partitions.
	 */
	protected FeatureTemplate[][] templates;

	/**
	 * Encoding for explicit features, i.e., features created from templates by
	 * conjoining basic features.
	 */
	protected MapEncoding<Feature> explicitEncoding;

	/**
	 * Number of template partitions.
	 */
	protected int numberOfPartitions;

	/**
	 * Current partition.
	 */
	protected int currentPartition;

	/**
	 * Feature labels.
	 */
	protected String[] featureLabels;

	/**
	 * Multi-valued features.
	 */
	protected Set<String> multiValuedFeatures;

	/**
	 * Input sequences.
	 */
	protected DPInput[] inputs;

	/**
	 * Output branchings.
	 */
	protected DPOutput[] outputs;

	/**
	 * Indicate if this dataset is a training set.
	 */
	protected boolean training;

	/**
	 * Length of the longest sequence in this dataset.
	 */
	protected int maxNumberOfTokens;

	/**
	 * Optional inverted index representation to speedup some algorithms.
	 * Mainly, training algorithms.
	 */
	private InvertedIndex invertedIndex;

	/**
	 * Punctuation file reader.
	 */
	protected BufferedReader readerPunc;

	/**
	 * Punctuation file name.
	 */
	protected String fileNamePunc;

	/**
	 * Create an empty dataset.
	 */
	public DPColumnDataset() {
		this.basicEncoding = new StringMapEncoding();
		this.explicitEncoding = new MapEncoding<Feature>();
		this.multiValuedFeatures = new TreeSet<String>();
	}

	/**
	 * Create edge corpus.
	 * 
	 * @param basicEncoding
	 * @param multiValuedFeatures
	 */
	public DPColumnDataset(FeatureEncoding<String> basicEncoding,
			Collection<String> multiValuedFeatures) {
		this.basicEncoding = basicEncoding;
		this.explicitEncoding = new MapEncoding<Feature>();
		this.multiValuedFeatures = new TreeSet<String>();
		if (multiValuedFeatures != null) {
			for (String ftrLabel : multiValuedFeatures)
				this.multiValuedFeatures.add(ftrLabel);
		}
	}

	/**
	 * Create a new dataset using the same encoding and other underlying data
	 * structures of the given 'sibling' dataset.
	 * 
	 * For most use cases, the underlying data structures within the sibling
	 * dataset should be kept unchanged after creating this new dataset.
	 * 
	 * @param sibling
	 */
	public DPColumnDataset(DPColumnDataset sibling) {
		this.multiValuedFeatures = sibling.multiValuedFeatures;
		this.basicEncoding = sibling.basicEncoding;
		this.explicitEncoding = sibling.explicitEncoding;
		this.templates = sibling.templates;
	}

	/**
	 * @return the number of features (columns) in this corpus.
	 */
	public int getNumberOfFeatures() {
		return featureLabels.length;
	}

	@Override
	public DPInput[] getInputs() {
		return inputs;
	}

	@Override
	public DPOutput[] getOutputs() {
		return outputs;
	}

	@Override
	public DPInput getInput(int index) {
		return inputs[index];
	}

	@Override
	public DPOutput getOutput(int index) {
		return outputs[index];
	}

	@Override
	public int getNumberOfExamples() {
		return inputs.length;
	}

	@Override
	public boolean isTraining() {
		return training;
	}

	/**
	 * Return the explicit feature encoding of this dataset.
	 * 
	 * @return
	 */
	public MapEncoding<Feature> getExplicitFeatureEncoding() {
		return explicitEncoding;
	}

	/**
	 * Return the optional inverted index that represents this corpus.
	 * 
	 * This data structure can be used to speedup training algorithms.
	 * 
	 * @return
	 */
	public InvertedIndex getInvertedIndex() {
		return invertedIndex;
	}

	/**
	 * Create an inverted index for this corpus.
	 */
	public void createInvertedIndex() {
		this.invertedIndex = new InvertedIndex(this);
	}

	/**
	 * Return the punctuation file name.
	 * 
	 * @return
	 */
	public String getFileNamePunc() {
		return fileNamePunc;
	}

	/**
	 * Set the name of the punctuation file for this edge corpus.
	 * 
	 * @param filename
	 */
	public void setFileNamePunc(String filename) {
		this.fileNamePunc = filename;
	}

	/**
	 * @return the lenght of the longest sequence in this dataset.
	 */
	public int getMaxNumberOfTokens() {
		return maxNumberOfTokens;
	}

	@Override
	public void load(String fileName) throws IOException, DatasetException {
		BufferedReader reader = new BufferedReader(new FileReader(fileName));
		load(reader);
		reader.close();
	}

	@Override
	public void load(InputStream is) throws IOException, DatasetException {
		load(new BufferedReader(new InputStreamReader(is)));
	}

	/**
	 * Return the feature index for the given label. If a feature with such
	 * label does not exist, return <code>-1</code>.
	 * 
	 * @param label
	 * @return
	 */
	public int getFeatureIndex(String label) {
		for (int idx = 0; idx < featureLabels.length; ++idx)
			if (featureLabels[idx].equals(label))
				return idx;
		return -1;
	}

	/**
	 * Return the label for the given feature index.
	 * 
	 * @param index
	 * @return
	 */
	public String getFeatureLabel(int index) {
		return featureLabels[index];
	}

	@Override
	public void load(BufferedReader reader) throws IOException,
			DatasetException {
		// Punctuation file.
		if (fileNamePunc != null)
			readerPunc = new BufferedReader(new FileReader(fileNamePunc));

		// Read feature labels in the first line of the file.
		String line = reader.readLine();
		reader.readLine();
		int eq = line.indexOf('=');
		int end = line.indexOf(']');
		String[] labels = line.substring(eq + 1, end).split(",");
		featureLabels = new String[labels.length - 2];
		for (int i = 1; i < labels.length - 1; ++i)
			featureLabels[i - 1] = labels[i].trim();

		// Multi-valued features indexes.
		Set<Integer> multiValuedFeaturesIndexes = new TreeSet<Integer>();
		for (String label : multiValuedFeatures)
			multiValuedFeaturesIndexes.add(getFeatureIndex(label));

		// Examples.
		List<DPInput> inputList = new LinkedList<DPInput>();
		List<DPOutput> outputList = new LinkedList<DPOutput>();
		int numExs = 0;
		while (parseExample(reader, multiValuedFeaturesIndexes, "|", inputList,
				outputList)) {
			++numExs;
			if ((numExs + 1) % 100 == 0) {
				System.out.print(".");
				System.out.flush();
			}
		}
		System.out.println();
		inputs = inputList.toArray(new DPInput[0]);
		outputs = outputList.toArray(new DPOutput[0]);

		// Close punctuation file.
		if (fileNamePunc != null)
			readerPunc.close();

		LOG.info("Read " + inputs.length + " examples.");
	}

	@Override
	public void save(String fileName) throws IOException, DatasetException {
		BufferedWriter writer = new BufferedWriter(new FileWriter(fileName));
		save(writer);
		writer.close();
	}

	@Override
	public void save(OutputStream os) throws IOException, DatasetException {
		save(new BufferedWriter(new OutputStreamWriter(os)));
	}

	@Override
	public void save(BufferedWriter writer) throws IOException,
			DatasetException {
		throw new NotImplementedException();
	}

	/**
	 * Save this dataset along with a new column with the given predicted
	 * outputs.
	 * 
	 * @param fileName
	 * @param predictedOuputs
	 * @throws IOException
	 * @throws DatasetException
	 */
	public void save(String fileName, DPOutput[] predictedOuputs)
			throws IOException, DatasetException {
		BufferedWriter writer = new BufferedWriter(new FileWriter(fileName));
		save(writer, predictedOuputs);
		writer.close();
	}

	/**
	 * Save this dataset along with a new column with the given predicted
	 * outputs.
	 * 
	 * @param os
	 * @param predictedOuputs
	 * @throws IOException
	 * @throws DatasetException
	 */
	public void save(OutputStream os, DPOutput[] predictedOuputs)
			throws IOException, DatasetException {
		save(new BufferedWriter(new OutputStreamWriter(os)), predictedOuputs);
	}

	/**
	 * Save this dataset along with a new column with the given predicted
	 * outputs.
	 * 
	 * @param writer
	 * @param predictedOuputs
	 * @throws IOException
	 * @throws DatasetException
	 */
	public void save(BufferedWriter writer, DPOutput[] predictedOuputs)
			throws IOException, DatasetException {
		// Header.
		writer.write("[features = id");
		for (int idxFtr = 0; idxFtr < featureLabels.length; ++idxFtr)
			writer.write(", " + featureLabels[idxFtr]);
		writer.write(", correct, predicted]\n\n");

		// Examples.
		for (int idxEx = 0; idxEx < inputs.length; ++idxEx) {
			DPInput input = inputs[idxEx];
			DPOutput correctOutput = outputs[idxEx];
			DPOutput predictedOutput = predictedOuputs[idxEx];

			// Edge features.
			int numTokens = input.getNumberOfTokens();
			for (int idxDep = 0; idxDep < numTokens; ++idxDep) {
				for (int idxHead = 0; idxHead < numTokens; ++idxHead) {
					int[] ftrs = input.getBasicFeatures(idxHead, idxDep);
					if (ftrs == null)
						continue;
					// Id.
					writer.write(idxHead + ">" + idxDep);
					// Features.
					for (int idxFtr = 0; idxFtr < ftrs.length; ++idxFtr)
						writer.write(" "
								+ basicEncoding.getValueByCode(ftrs[idxFtr]));

					// Correct feature.
					if (correctOutput.getHead(idxDep) == idxHead)
						writer.write(" Y");
					else
						writer.write(" N");

					// Predicted feature.
					if (predictedOutput.getHead(idxDep) == idxHead)
						writer.write(" Y");
					else
						writer.write(" N");

					writer.write("\n");
				}
			}

			writer.write("\n");
		}
	}

	/**
	 * Parse an example from the given reader.
	 * 
	 * An example is a sequence of edges. Each edge is represented in one line.
	 * Blank lines separate one example from the next one. Each line (edge) is a
	 * sequence of feature values (in the order that was presented in the file
	 * header).
	 * 
	 * The first feature of each edge comprises its ID that *must* obey the
	 * format "[head token index]>[dependent token index]" to indicate end
	 * points of the directed edge. The last feature is equal to "TRUE" if the
	 * edge is part of the correct dependecy tree of this example and "FALSE"
	 * otherwise. The remaining values are the ordinary basic features.
	 * 
	 * Edge can be ommited and then will be considered inexistent.
	 * 
	 * @param reader
	 *            input file reader positioned at the beginning of an example or
	 *            at the end of the file.
	 * @param multiValuedFeatureIndexes
	 *            which features are multi-valued features.
	 * @param valueSeparator
	 *            character sequence that separates values within a multi-valued
	 *            feature.
	 * @param inputList
	 *            the list of input structures to store the read input.
	 * @param outputList
	 *            the list of output structures to store the read output.
	 * @return
	 * @throws IOException
	 *             if there is a problem reading the input file.
	 * @throws DatasetException
	 *             if there is a syntax or semantic issue.
	 */
	protected boolean parseExample(BufferedReader reader,
			Set<Integer> multiValuedFeatureIndexes, String valueSeparator,
			List<DPInput> inputList, List<DPOutput> outputList)
			throws IOException, DatasetException {
		// Global variables.
		int numTokens = -1;
		String id = null;

		/*
		 * Read the (optional) punctuation file. This file should contain two
		 * lines for each example in the dataset input file (in the same order)
		 * and one blank line separating examples. The first line contains the
		 * example id and the second contains a sequence of values comprising
		 * one value for each token in the corresponding example. For each
		 * token, if this value is "punc", then this token will be ignored in
		 * the evaluation. If the value is "no", then this token is considered
		 * for evaluation matters.
		 */
		String[] puncs = null;
		boolean[] punctuation = null;
		if (readerPunc != null) {
			// Example id.
			id = readerPunc.readLine();
			// Sequence of punctuation indicators.
			String puncLine = readerPunc.readLine();
			if (puncLine != null) {
				// Skip blank line.
				readerPunc.readLine();
				// Punctuation flags separated by space.
				puncs = REGEX_SPACE.split(puncLine);
				numTokens = puncs.length;
				punctuation = new boolean[numTokens];
				for (int idxTkn = 0; idxTkn < numTokens; ++idxTkn)
					punctuation[idxTkn] = puncs[idxTkn].equals("punc");
			}
		}

		/*
		 * List of edges. Each edge is a list of feature codes. However, the two
		 * first values in each list are the head token index and the dependent
		 * token index, and the third value is 1, if the edge is correct, and 0,
		 * otherwise.
		 */
		LinkedList<LinkedList<Integer>> features = new LinkedList<LinkedList<Integer>>();

		// Correct edges (head-dependent pairs).
		LinkedList<Integer> correctDepTokens = new LinkedList<Integer>();
		LinkedList<Integer> correctHeadTokens = new LinkedList<Integer>();

		// Maximum token index.
		int maxIndex = -1;

		// Read next line.
		String line;
		while ((line = reader.readLine()) != null) {

			line = line.trim();
			if (line.length() == 0)
				// Stop on blank lines.
				break;

			// Split edge in feature values.
			String[] ftrValues = REGEX_SPACE.split(line);

			// Head and dependent tokens indexes.
			String[] edgeId = ftrValues[0].split(">");
			int idxHead = Integer.parseInt(edgeId[0]);
			int idxDep = Integer.parseInt(edgeId[1]);

			// Skip diagonal edges.
			if (idxDep == idxHead)
				continue;

			if (idxHead > maxIndex)
				maxIndex = idxHead;
			if (idxDep > maxIndex)
				maxIndex = idxDep;

			// List of feature codes.
			LinkedList<Integer> edgeFeatures = new LinkedList<Integer>();
			features.add(edgeFeatures);

			// The two first values are the head and the dependent indexes.
			edgeFeatures.add(idxHead);
			edgeFeatures.add(idxDep);

			// Encode the edge features.
			for (int idxFtr = 1; idxFtr < ftrValues.length - 1; ++idxFtr) {
				String str = ftrValues[idxFtr];
				// TODO deal with multi-valued features.
				int code = basicEncoding.put(new String(str));
				edgeFeatures.add(code);
			}

			// The last value is the correct edge flag (TRUE or FALSE).
			String isCorrectEdge = ftrValues[ftrValues.length - 1];
			if (isCorrectEdge.equals("Y")) {
				correctDepTokens.add(idxDep);
				correctHeadTokens.add(idxHead);
			} else if (!isCorrectEdge.equals("N")) {
				throw new DatasetException(
						"Last feature value must be Y or N to indicate "
								+ "the correct edge. However, for token "
								+ idxDep + " and head " + idxHead
								+ " this feature value is " + isCorrectEdge);
			}
		}

		if (features.size() == 0)
			return line != null;

		if (numTokens == -1)
			numTokens = maxIndex + 1;

		if (id == null)
			id = "" + inputList.size();

		// Allocate the output structure.
		DPOutput output = new DPOutput(numTokens);
		// Fill the output structure.
		Iterator<Integer> itDep = correctDepTokens.iterator();
		Iterator<Integer> itHead = correctHeadTokens.iterator();
		while (itDep.hasNext() && itHead.hasNext()) {
			int idxDep = itDep.next();
			int idxHead = itHead.next();
			if (output.getHead(idxDep) != -1)
				LOG.warn("Multiple correct incoming edges for token " + idxDep
						+ " in example " + id);
			output.setHead(idxDep, idxHead);
		}

		/*
		 * Create a new string to store the input id to avoid memory leaks,
		 * since the id string keeps a reference to the line string.
		 */
		DPInput input = new DPInput(numTokens, new String(id), features, false);
		if (punctuation != null)
			input.setPunctuation(punctuation);

		// Keep the length of the longest sequence.
		int len = input.getNumberOfTokens();
		if (len > maxNumberOfTokens)
			maxNumberOfTokens = len;

		inputList.add(input);
		outputList.add(output);

		// Return true if there are more lines.
		return line != null;
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

	@Override
	public FeatureEncoding<String> getFeatureEncoding() {
		return basicEncoding;
	}

	/**
	 * Return the explicit features encoding.
	 * 
	 * @return
	 */
	public MapEncoding<Feature> getExplicitEncoding() {
		return explicitEncoding;
	}

	@Override
	public void serialize(String filename) throws FileNotFoundException,
			IOException {
		throw new NotImplementedException();
	}

	@Override
	public void serialize(String inFilename, String outFilename)
			throws IOException, DatasetException {
		throw new NotImplementedException();
	}

	@Override
	public void deserialize(String filename) throws FileNotFoundException,
			IOException, ClassNotFoundException {
		throw new NotImplementedException();
	}

	// /**
	// * Store the accumulated weight of each edge for the current template
	// * partition and generate the features for the next partition.
	// *
	// * @return the next partition.
	// */
	// public int nextPartition() {
	// // Input structures.
	// int numExs = inputs.length;
	//
	// // Accumulate current partition feature weights.
	// for (int idxEx = 0; idxEx < numExs; ++idxEx) {
	// DPInput input = inputs[idxEx];
	// int numTkns = input.getNumberOfTokens();
	// for (int idxHead = 0; idxHead < numTkns; ++idxHead) {
	// for (int idxDep = 0; idxDep < numTkns; ++idxDep) {
	// double score = getEdgeScoreFromCurrentFeatures(input,
	// idxHead, idxDep);
	// if (!Double.isNaN(score))
	// fixedWeights[idxEx][idxHead][idxDep] += score;
	// }
	// }
	// }
	//
	// // Go to next partition and generate new features.
	// ++currentPartition;
	// if (currentPartition < numberOfPartitions)
	// generateFeatures();
	// return currentPartition;
	// }

	/**
	 * Load templates from the given reader and, optionally, generate explicit
	 * features.
	 * 
	 * @param reader
	 * @param generateFeatures
	 * @throws IOException
	 */
	public void loadTemplates(BufferedReader reader, boolean generateFeatures)
			throws IOException {
		LinkedList<FeatureTemplate> templatesList = new LinkedList<FeatureTemplate>();
		String line = skipBlanksAndComments(reader);
		while (line != null) {
			String[] ftrsStr = REGEX_SPACE.split(line);
			int[] ftrs = new int[ftrsStr.length];
			for (int idx = 0; idx < ftrs.length; ++idx)
				ftrs[idx] = getFeatureIndex(ftrsStr[idx]);
			templatesList.add(new SimpleFeatureTemplate(templatesList.size(),
					ftrs));
			// Read next line.
			line = skipBlanksAndComments(reader);
		}

		// Convert list to array.
		numberOfPartitions = 1;
		templates = new FeatureTemplate[1][];
		templates[0] = templatesList.toArray(new FeatureTemplate[0]);

		if (generateFeatures)
			// Generate explicit features.
			generateFeatures();
	}

	/**
	 * Load templates from the given file and, optionally, generate explicit
	 * features.
	 * 
	 * @param templatesFileName
	 * @param generateFeatures
	 * @throws IOException
	 */
	public void loadTemplates(String templatesFileName, boolean generateFeatures)
			throws IOException {
		BufferedReader reader = new BufferedReader(new FileReader(
				templatesFileName));
		loadTemplates(reader, generateFeatures);
		reader.close();
	}

	public void allocFeatureMatrix() {
		int numExs = inputs.length;
		for (int idxEx = 0; idxEx < numExs; ++idxEx) {
			// Current input structure.
			DPInput input = inputs[idxEx];
			// Allocate explicit features matrix.
			input.allocFeatureMatrix();
		}
	}

	/**
	 * Return template set.
	 * 
	 * @return
	 */
	public FeatureTemplate[][] getTemplates() {
		return templates;
	}

	/**
	 * Set the template set of this dataset.
	 */
	public void setTemplates(FeatureTemplate[][] tpls) {
		this.templates = tpls;
	}

	/**
	 * Generate features for the current template partition.
	 */
	public void generateFeatures() {
		LinkedList<Integer> ftrs = new LinkedList<Integer>();
		FeatureTemplate[] tpls = templates[currentPartition];
		int numExs = inputs.length;
		for (int idxEx = 0; idxEx < numExs; ++idxEx) {
			// Current input structure.
			DPInput input = inputs[idxEx];

			// Number of tokens within the current input.
			int numTkns = input.getNumberOfTokens();

			// Allocate explicit features matrix.
			input.allocFeatureMatrix();

			for (int idxHead = 0; idxHead < numTkns; ++idxHead) {
				for (int idxDep = 0; idxDep < numTkns; ++idxDep) {
					// Skip non-existent edges.
					if (input.getBasicFeatures(idxHead, idxDep) == null)
						continue;

					// Clear previous used list of features.
					ftrs.clear();

					/*
					 * Instantiate edge features and add them to active features
					 * list.
					 */
					for (int idxTpl = 0; idxTpl < tpls.length; ++idxTpl) {
						FeatureTemplate tpl = tpls[idxTpl];
						// Get temporary feature instance.
						Feature ftr = tpl.getInstance(input, idxHead, idxDep);
						// Lookup the feature in the encoding.
						int code = explicitEncoding.getCodeByValue(ftr);
						/*
						 * Instantiate a new feature, if it is not present in
						 * the encoding.
						 */
						if (code == FeatureEncoding.UNSEEN_VALUE_CODE)
							code = explicitEncoding.put(tpl.newInstance(input,
									idxHead, idxDep));
						// Add feature code to active features list.
						ftrs.add(code);
					}

					// Set feature vector of this input.
					input.setFeatures(idxHead, idxDep, ftrs, ftrs.size());
				}
			}

			// Progess report.
			if ((idxEx + 1) % 100 == 0) {
				System.out.print('.');
				System.out.flush();
			}
		}

		System.out.println();
		System.out.flush();
	}

	/**
	 * For testing.
	 */
	public void generateBasicFeatures() {
		LinkedList<Integer> ftrs = new LinkedList<Integer>();
		int numExs = inputs.length;
		for (int idxEx = 0; idxEx < numExs; ++idxEx) {
			// Current input structure.
			DPInput input = inputs[idxEx];

			// Number of tokens within the current input.
			int numTkns = input.getNumberOfTokens();

			// Allocate explicit features matrix.
			input.allocFeatureMatrix();

			for (int idxHead = 0; idxHead < numTkns; ++idxHead) {
				for (int idxDep = 0; idxDep < numTkns; ++idxDep) {
					// Skip non-existent edges.
					int[] basicFtrs = input.getBasicFeatures(idxHead, idxDep);
					if (basicFtrs == null)
						continue;

					// Clear previous used list of features.
					ftrs.clear();

					/*
					 * Instantiate edge features and add them to active features
					 * list.
					 */
					for (int idxFtr = 0; idxFtr < basicFtrs.length; ++idxFtr) {
						int code = basicFtrs[idxFtr];
						ftrs.add(code);
					}

					// Set feature vector of this input.
					input.setFeatures(idxHead, idxDep, ftrs, ftrs.size());
				}
			}

			// Progess report.
			if ((idxEx + 1) % 100 == 0) {
				System.out.print('.');
				System.out.flush();
			}
		}

		System.out.println();
		System.out.flush();
	}
}
