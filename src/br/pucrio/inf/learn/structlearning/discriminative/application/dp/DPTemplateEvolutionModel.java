package br.pucrio.inf.learn.structlearning.discriminative.application.dp;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.json.JSONWriter;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import br.pucrio.inf.learn.structlearning.discriminative.application.coreference.CorefColumnDataset;
import br.pucrio.inf.learn.structlearning.discriminative.application.dp.data.DPColumnDataset;
import br.pucrio.inf.learn.structlearning.discriminative.application.dp.data.DPInput;
import br.pucrio.inf.learn.structlearning.discriminative.application.dp.data.DPOutput;
import br.pucrio.inf.learn.structlearning.discriminative.application.sequence.AveragedParameter;
import br.pucrio.inf.learn.structlearning.discriminative.data.Dataset;
import br.pucrio.inf.learn.structlearning.discriminative.data.DatasetException;
import br.pucrio.inf.learn.structlearning.discriminative.data.ExampleInput;
import br.pucrio.inf.learn.structlearning.discriminative.data.ExampleOutput;
import br.pucrio.inf.learn.structlearning.discriminative.data.encoding.FeatureEncoding;
import br.pucrio.inf.learn.structlearning.discriminative.data.encoding.MapEncoding;

/**
 * Represent a dependecy parsing model (head-dependent edge parameters) by means
 * of a set of templates that conjoing basic features within the input
 * structure.
 * 
 * In this version, templates are partitioned and each partition is used once at
 * a time. For each partition, some learning iterations are performed
 * considering only the features from this template partition. Then, the current
 * weights for these features are fixed and the corresponding accumulated
 * weights for each edge is stored for efficiency matter and the next template
 * partition is used for the next learning iterations.
 * 
 * 
 * @author eraldo
 * 
 */
public class DPTemplateEvolutionModel implements DPModel {

	/**
	 * Logging object.
	 */
	private static Log LOG = LogFactory.getLog(DPTemplateEvolutionModel.class);

	/**
	 * Special root node.
	 */
	protected int root;

	/**
	 * Weight for each feature code (model parameters).
	 */
	protected Map<Integer, AveragedParameter> parameters;

	/**
	 * Set of parameters that have been updated in the current iteration.
	 */
	protected Set<AveragedParameter> updatedParameters;

	/**
	 * Create a new model with the given template partitions.
	 * 
	 * @param root
	 *            index of the special node that is to be considered as root.
	 */
	public DPTemplateEvolutionModel(int root) {
		this.root = root;
		this.parameters = new HashMap<Integer, AveragedParameter>();
		this.updatedParameters = new HashSet<AveragedParameter>();
	}

	/**
	 * Load a model from the given file and using the encodings in the given
	 * dataset. Usually, the loaded model will later be applied in this dataset.
	 * The dataset encodins can be even empty and then they will be filled with
	 * features from the loaded model.
	 * 
	 * @param fileName
	 * @param dataset
	 * @param largeModel
	 * @throws JSONException
	 * @throws IOException
	 * @throws DatasetException
	 */
	public DPTemplateEvolutionModel(String fileName,
			CorefColumnDataset dataset, boolean largeModel)
			throws JSONException, IOException, DatasetException {
		this.updatedParameters = null;
		this.parameters = new HashMap<Integer, AveragedParameter>();

		// Model file input stream.
		FileInputStream fis = new FileInputStream(fileName);

		if (!largeModel) {
			/*
			 * Load using the JSON reference implementation:
			 * https://github.com/douglascrockford/JSON-java.
			 */

			// Load JSON model object.
			JSONObject jModel = new JSONObject(new JSONTokener(fis));

			// Set dataset templates.
			LOG.info("Loading templates...");
			FeatureTemplate[][] templatesAllLevels = loadTemplatesFromJSON(
					jModel, dataset);
			dataset.setTemplates(templatesAllLevels);

			// Set model parameters.
			LOG.info("Loading parameters...");
			loadParametersFromJSON(jModel, dataset);

			// Close model file input stream.
			fis.close();

			// Get root value.
			this.root = jModel.getInt("root");
		} else {
			/*
			 * Load using Jackson high-performance streaming library:
			 * http://jackson.codehaus.org/
			 */
			JsonFactory jf = new JsonFactory();
			JsonParser jp = jf.createJsonParser(new File(fileName));
			if (jp.nextToken() != JsonToken.START_OBJECT) {
				String msg = String.format(
						"Model file (%s) should contain an object", fileName);
				LOG.error(msg);
				throw new DatasetException(msg);
			}

			while (jp.nextToken() != JsonToken.END_OBJECT) {
				String fName = jp.getCurrentName();
				if ("root".equals(fName)) {
					// Root node index.
					if (jp.nextToken() != JsonToken.VALUE_NUMBER_INT) {
						String msg = String
								.format("Root value should be an integer");
						LOG.error(msg);
						throw new DatasetException(msg);
					}
					root = jp.getValueAsInt();
				} else if ("templates".equals(fName)) {
					// Feature templates.
					LOG.info("Loading templates...");
					FeatureTemplate[][] templatesAllLevels = loadTemplatesFromJackson(
							jp, dataset);
					dataset.setTemplates(templatesAllLevels);
				} else if ("parameters".equals(fName)) {
					// Model parameters.
					LOG.info("Loading parameters...");
					loadParametersFromJackson(jp, dataset);
				}
			}
		}
	}

	/**
	 * Load parameters using Jackson JSON library.
	 * 
	 * @param jp
	 * @param dataset
	 * @throws JsonParseException
	 * @throws IOException
	 * @throws DatasetException
	 */
	private void loadParametersFromJackson(JsonParser jp,
			CorefColumnDataset dataset) throws JsonParseException, IOException,
			DatasetException {
		if (jp.nextToken() != JsonToken.START_ARRAY)
			throw new DatasetException("Error parsing parameters");
		MapEncoding<Feature> explicitEncoding = dataset
				.getExplicitFeatureEncoding();
		FeatureEncoding<String> basicEncoding = dataset.getFeatureEncoding();
		while (jp.nextToken() != JsonToken.END_ARRAY) {
			// Skip start array token for this parameter.
			// Each parameter follows the format: [ idxTpl, [vals], weight ]
			if (jp.nextToken() != JsonToken.VALUE_NUMBER_INT)
				throw new DatasetException("Error parsing parameters");
			// Template index.
			int idxTpl = jp.getIntValue();
			// Skip start array token for the feature values.
			if (jp.nextToken() != JsonToken.START_ARRAY)
				throw new DatasetException(
						"Error parsing parameter feature values");
			// List of values.
			LinkedList<Integer> valuesL = new LinkedList<Integer>();
			while (jp.nextToken() != JsonToken.END_ARRAY)
				valuesL.add(basicEncoding.put(jp.getText()));
			// Convert the list of values to an array.
			int[] values = new int[valuesL.size()];
			int idxVal = 0;
			for (int val : valuesL)
				values[idxVal++] = val;
			// Create a feature object and encode it.
			Feature ftr = new Feature(idxTpl, values);
			int code = explicitEncoding.put(ftr);
			// Put the new parameter weight in the parameters dictionary.
			if (jp.nextToken() != JsonToken.VALUE_NUMBER_FLOAT
					&& jp.getCurrentToken() != JsonToken.VALUE_NUMBER_INT)
				throw new DatasetException(
						"Error parsing parameter feature values");
			parameters.put(code, new AveragedParameter(jp.getDoubleValue()));
			// Skip the end array token.
			if (jp.nextToken() != JsonToken.END_ARRAY)
				throw new DatasetException("Error parsing parameters");
		}
	}

	/**
	 * Load feature templates using JSON Jackson library.
	 * 
	 * @param jp
	 * @param dataset
	 * @return
	 * @throws JsonParseException
	 * @throws IOException
	 * @throws DatasetException
	 */
	private FeatureTemplate[][] loadTemplatesFromJackson(JsonParser jp,
			CorefColumnDataset dataset) throws JsonParseException, IOException,
			DatasetException {
		/*
		 * Next two tokens are array starts, since templates are represented as
		 * an array of arrays of templates.
		 */
		if (jp.nextToken() != JsonToken.START_ARRAY
				|| jp.nextToken() != JsonToken.START_ARRAY) {
			String msg = String.format("Error parsing templates");
			LOG.error(msg);
			throw new DatasetException(msg);
		}

		/*
		 * Parse the first array of templates. In this code, we are considering
		 * only one (the first one) template set. However, there can be more
		 * sets.
		 */
		int idxTpl = 0;
		LinkedList<FeatureTemplate> templatesL = new LinkedList<FeatureTemplate>();
		while (jp.nextToken() != JsonToken.END_ARRAY) {
			// Parse one template, i.e., a list of feature labels.
			LinkedList<Integer> ftrsL = new LinkedList<Integer>();
			while (jp.nextToken() != JsonToken.END_ARRAY)
				ftrsL.add(dataset.getFeatureIndex(jp.getText()));
			// Convert list of features to array.
			int[] ftrsV = new int[ftrsL.size()];
			int idxFtr = 0;
			for (int ftr : ftrsL)
				ftrsV[idxFtr++] = ftr;
			// Create a new template and add it to the list of templates.
			templatesL.add(new SimpleFeatureTemplate(idxTpl, ftrsV));
			++idxTpl;
		}

		jp.nextToken();

		// Convert the list of templates to an array.
		FeatureTemplate[][] templatesVAllLevels = new FeatureTemplate[1][];
		templatesVAllLevels[0] = new FeatureTemplate[templatesL.size()];
		templatesL.toArray(templatesVAllLevels[0]);

		return templatesVAllLevels;
	}

	/**
	 * Copy constructor.
	 * 
	 * @param other
	 * @throws CloneNotSupportedException
	 */
	@SuppressWarnings("unchecked")
	protected DPTemplateEvolutionModel(DPTemplateEvolutionModel other)
			throws CloneNotSupportedException {
		// Root node.
		this.root = other.root;

		// Shallow-copy parameters map.
		this.parameters = (HashMap<Integer, AveragedParameter>) ((HashMap<Integer, AveragedParameter>) other.parameters)
				.clone();

		// Clone each map value.
		for (Entry<Integer, AveragedParameter> entry : parameters.entrySet())
			entry.setValue(entry.getValue().clone());

		// Updated parameters and features are NOT copied.
		updatedParameters = new TreeSet<AveragedParameter>();
	}

	/**
	 * Load model parameters from the given JSON model object
	 * <code>jModel</code>.
	 * 
	 * @param jModel
	 * @param dataset
	 * @throws JSONException
	 */
	protected void loadParametersFromJSON(JSONObject jModel,
			CorefColumnDataset dataset) throws JSONException {
		// Encodings.
		FeatureEncoding<String> basicEncoding = dataset.getFeatureEncoding();
		FeatureEncoding<Feature> explicitEncoding = dataset
				.getExplicitEncoding();
		// JSON array of parameters.
		JSONArray jParams = jModel.getJSONArray("parameters");
		int numParams = jParams.length();
		LOG.info(String.format("Loading %d parameters...", numParams));
		for (int idxParam = 0; idxParam < numParams; ++idxParam) {
			/*
			 * JSON array that represents a complete parameter: its template
			 * index, its feature values and its weight.
			 */
			JSONArray jParam = jParams.getJSONArray(idxParam);
			// Template index.
			int idxTpl = jParam.getInt(0);
			// Copy basic features values.
			JSONArray jValues = jParam.getJSONArray(1);
			int[] values = new int[jValues.length()];
			for (int idxVal = 0; idxVal < values.length; ++idxVal)
				values[idxVal] = basicEncoding.put(jValues.getString(idxVal));
			// Create a feature object and encode it.
			Feature ftr = new Feature(idxTpl, values);
			int code = explicitEncoding.put(ftr);
			// Put the new feature weight in the parameters.
			parameters.put(code, new AveragedParameter(jParam.getDouble(2)));
		}
	}

	/**
	 * Load templates from the given JSON model object <code>jModel</code>.
	 * 
	 * @param jModel
	 * @param dataset
	 * @return
	 * @throws JSONException
	 */
	protected FeatureTemplate[][] loadTemplatesFromJSON(JSONObject jModel,
			CorefColumnDataset dataset) throws JSONException {
		// Get template set.
		JSONArray jTemplatesAllLevels = jModel.getJSONArray("templates");
		FeatureTemplate[][] templatesAllLevels = new FeatureTemplate[jTemplatesAllLevels
				.length()][];
		for (int level = 0; level < templatesAllLevels.length; ++level) {
			JSONArray jTemplates = jTemplatesAllLevels.getJSONArray(level);
			FeatureTemplate[] templates = new FeatureTemplate[jTemplates
					.length()];
			for (int idxTpl = 0; idxTpl < templates.length; ++idxTpl) {
				JSONArray jTemplate = jTemplates.getJSONArray(idxTpl);
				int[] features = new int[jTemplate.length()];
				for (int idxFtr = 0; idxFtr < features.length; ++idxFtr)
					features[idxFtr] = dataset.getFeatureIndex(jTemplate
							.getString(idxFtr));
				SimpleFeatureTemplate tpl = new SimpleFeatureTemplate(idxTpl,
						features);
				templates[idxTpl] = tpl;
			}
			templatesAllLevels[level] = templates;
		}
		return templatesAllLevels;
	}

	/**
	 * Return the parameters map.
	 * 
	 * @return
	 */
	public Map<Integer, AveragedParameter> getParameters() {
		return parameters;
	}

	/**
	 * Return an edge weight based only on the current features in
	 * <code>activeFeatures</code> list.
	 * 
	 * @param input
	 * @param idxHead
	 * @param idxDependent
	 * @return
	 */
	protected double getEdgeScoreFromCurrentFeatures(DPInput input,
			int idxHead, int idxDependent) {
		// Get list of feature codes in the given edge.
		int[] features = input.getFeatures(idxHead, idxDependent);

		// Check edge existence.
		if (features == null)
			return Double.NaN;

		double score = 0d;
		for (int idxFtr = 0; idxFtr < features.length; ++idxFtr) {
			AveragedParameter param = parameters.get(features[idxFtr]);
			if (param != null)
				score += param.get();
		}

		return score;
	}

	@Override
	public double getEdgeScore(DPInput input, int idxHead, int idxDependent) {
		// int idxEx = input.getTrainingIndex();
		double score = getEdgeScoreFromCurrentFeatures(input, idxHead,
				idxDependent);
		return score;
	}

	@Override
	public double update(ExampleInput input, ExampleOutput outputCorrect,
			ExampleOutput outputPredicted, double learningRate) {
		return update((DPInput) input, (DPOutput) outputCorrect,
				(DPOutput) outputPredicted, learningRate);
	}

	/**
	 * Update this model using the differences between the correct output and
	 * the predicted output, both given as arguments.
	 * 
	 * @param input
	 * @param outputCorrect
	 * @param outputPredicted
	 * @param learningRate
	 * @return
	 */
	protected double update(DPInput input, DPOutput outputCorrect,
			DPOutput outputPredicted, double learningRate) {
		/*
		 * The root token must always be ignored during the inference, thus it
		 * has to be always correctly classified.
		 */
		assert outputCorrect.getHead(root) == outputPredicted.getHead(root);

		// Per-token loss value for this example.
		double loss = 0d;
		for (int idxTkn = 0; idxTkn < input.getNumberOfTokens(); ++idxTkn) {
			// Correct head token.
			int idxCorrectHead = outputCorrect.getHead(idxTkn);

			// Predicted head token.
			int idxPredictedHead = outputPredicted.getHead(idxTkn);

			// Skip. Correctly predicted head.
			if (idxCorrectHead == idxPredictedHead)
				continue;

			if (idxCorrectHead == -1)
				/*
				 * Skip tokens with missing CORRECT edge (this is due to prune
				 * preprocessing).
				 */
				continue;

			/*
			 * Misclassified head for this token. Thus, update edges parameters.
			 */

			// Increment parameter weights for correct edge features.
			int[] correctFeatures = input.getFeatures(idxCorrectHead, idxTkn);
			if (correctFeatures != null)
				for (int idxFtr = 0; idxFtr < correctFeatures.length; ++idxFtr)
					updateFeatureParam(correctFeatures[idxFtr], learningRate);

			if (idxPredictedHead == -1)
				continue;

			/*
			 * Decrement parameter weights for incorrectly predicted edge
			 * features.
			 */
			int[] predictedFeatures = input.getFeatures(idxPredictedHead,
					idxTkn);
			if (predictedFeatures != null)
				for (int idxFtr = 0; idxFtr < predictedFeatures.length; ++idxFtr)
					updateFeatureParam(predictedFeatures[idxFtr], -learningRate);

			// Increment (per-token) loss value.
			loss += 1d;
		}

		return loss;
	}

	/**
	 * Recover the parameter associated with the given feature.
	 * 
	 * If the parameter has not been initialized yet, then create it. If the
	 * inverted index is activated and the parameter has not been initialized
	 * yet, then update the active features lists for each edge where the
	 * feature occurs.
	 * 
	 * @param ftr
	 * @param value
	 * @return
	 */
	protected void updateFeatureParam(int code, double value) {
		AveragedParameter param = parameters.get(code);
		if (param == null) {
			// Create a new parameter.
			param = new AveragedParameter();
			parameters.put(code, param);
		}

		// Update parameter value.
		param.update(value);

		// Keep track of updated parameter within this example.
		updatedParameters.add(param);
	}

	@Override
	public void sumUpdates(int iteration) {
		for (AveragedParameter parm : updatedParameters)
			parm.sum(iteration);
		updatedParameters.clear();
	}

	@Override
	public void average(int numberOfIterations) {
		for (AveragedParameter parm : parameters.values())
			parm.average(numberOfIterations);
	}

	@Override
	public DPTemplateEvolutionModel clone() throws CloneNotSupportedException {
		return new DPTemplateEvolutionModel(this);
	}

	@Override
	public int getNumberOfUpdatedParameters() {
		return parameters.size();
	}

	@Override
	public void save(String fileName, Dataset dataset) throws IOException,
			FileNotFoundException {
		FileWriter fw = new FileWriter(fileName);
		save(fw, (DPColumnDataset) dataset);
		fw.close();
	}

	/**
	 * Save this model in the given <code>FileWriter</code> object.
	 * 
	 * @param w
	 * @param dataset
	 * @throws IOException
	 */
	public void save(Writer w, DPColumnDataset dataset) throws IOException {
		FeatureEncoding<String> basicEncoding = dataset.getFeatureEncoding();
		FeatureEncoding<Feature> explicitEncoding = dataset
				.getExplicitEncoding();
		try {
			// JSON objects writer.
			JSONWriter jw = new JSONWriter(w);

			// Model object.
			jw.object();

			// Root value.
			jw.key("root").value(root);

			// Templates array.
			jw.key("templates");
			jw.array();
			FeatureTemplate[][] templatesAllLevels = dataset.getTemplates();
			for (FeatureTemplate[] templates : templatesAllLevels) {
				// Templates array of the current level.
				jw.array();
				for (FeatureTemplate template : templates) {
					// Features array of the current template.
					jw.array();
					for (int idxFtr : template.getFeatures())
						jw.value(dataset.getFeatureLabel(idxFtr));
					// End of features array of the current template.
					jw.endArray();
				}
				// End of templates array of the current level.
				jw.endArray();
			}
			// End of templates array.
			jw.endArray();

			// Parameters array.
			jw.key("parameters");
			jw.array();
			for (Entry<Integer, AveragedParameter> entry : parameters
					.entrySet()) {
				// Explicit features array:
				// [template_index, [feature_values_array], weight].
				jw.array();
				Feature ftr = explicitEncoding.getValueByCode(entry.getKey());
				jw.value(ftr.getTemplateIndex());
				// Feature values array.
				jw.array();
				for (int code : ftr.getValues())
					jw.value(basicEncoding.getValueByCode(code));
				// End of feature values array.
				jw.endArray();
				// Parameter weight.
				jw.value(entry.getValue().get());
				// End of explicit features array.
				jw.endArray();
			}
			// End of parameters array.
			jw.endArray();

			// End of model object.
			jw.endObject();

		} catch (JSONException e) {
			throw new IOException("JSON error", e);
		}
	}

	/**
	 * Sum the parameters of the given model in this model. The given model
	 * parameters are weighted by the given weight.
	 * 
	 * @param model
	 * @param weight
	 */
	public void sumModel(DPTemplateEvolutionModel model, double weight) {
		for (Entry<Integer, AveragedParameter> entry : model.parameters
				.entrySet()) {
			int code = entry.getKey();
			double val = entry.getValue().get();
			AveragedParameter param = parameters.get(code);
			if (param == null) {
				param = new AveragedParameter();
				parameters.put(code, param);
			}
			param.increment(val * weight);
		}
	}
}
