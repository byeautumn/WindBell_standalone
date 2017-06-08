package com.byeautumn.wb.dl;

import com.byeautumn.wb.data.CSVFilenameFilter;
import com.byeautumn.wb.data.OHLCUtils;
import com.byeautumn.wb.output.BasicLSTMDataGenerator;
import com.byeautumn.wb.output.ILabelClass;
import com.byeautumn.wb.output.LabelClass7;

import org.apache.commons.io.FileUtils;
import org.datavec.api.records.reader.SequenceRecordReader;
import org.datavec.api.records.reader.impl.csv.CSVSequenceRecordReader;
import org.datavec.api.split.NumberedFileInputSplit;
import org.deeplearning4j.datasets.datavec.SequenceRecordReaderDataSetIterator;
import org.deeplearning4j.eval.Evaluation;
import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.GradientNormalization;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.Updater;
import org.deeplearning4j.nn.conf.layers.GravesLSTM;
import org.deeplearning4j.nn.conf.layers.RnnOutputLayer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.optimize.listeners.ScoreIterationListener;
import org.deeplearning4j.util.ModelSerializer;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.api.DataSet;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.dataset.api.preprocessor.DataNormalization;
import org.nd4j.linalg.dataset.api.preprocessor.NormalizerStandardize;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.lossfunctions.LossFunctions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;

/**
 * Created by qiangao on 6/4/2017.
 */
public class BigPoolLSTMRunner {
	private static final Logger log = LoggerFactory.getLogger(BigPoolLSTMRunner.class);
	private RunnerConfigFileReader configReader;
	private ILabelClass labelClass = null;
	
	public BigPoolLSTMRunner(RunnerConfigFileReader configReader)
	{
		this.configReader = configReader;
		this.labelClass = getLabelClass(configReader);
	}
	
	private ILabelClass getLabelClass(RunnerConfigFileReader configReader)
	{
        String labelClasssName = configReader.getProperty("labelClassName");
        return DLUtils.getLabelClassInstance(labelClasssName);
	}
	
    public MultiLayerNetwork buildNetworkModel()
    {
        int numLabelClasses = labelClass.getNumLabels();
        int numFeatures = DLUtils.detectNumFeaturesFromTrainingData(configReader);
        int neuralSizeMultiplyer = Integer.parseInt(configReader.getProperty("neuralSizeMultiplyer"));
        int numHiddenLayers = Integer.parseInt(configReader.getProperty("numHiddenLayers"));
        int widthHiddenLayers = numFeatures * neuralSizeMultiplyer;

        NeuralNetConfiguration.Builder builder = new NeuralNetConfiguration.Builder();
        builder.seed(123)    //Random number generator seed for improved repeatability. Optional.
                .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT).iterations(1)
                .weightInit(WeightInit.XAVIER)
                .updater(Updater.NESTEROVS).momentum(0.9)
                .learningRate(0.003)
                .gradientNormalization(GradientNormalization.ClipElementWiseAbsoluteValue)  //Not always required, but helps with this data set
                .gradientNormalizationThreshold(0.5);

        NeuralNetConfiguration.ListBuilder listBuilder = builder.list();

        for (int i = 0; i < numHiddenLayers; i++) {
            GravesLSTM.Builder hiddenLayerBuilder = new GravesLSTM.Builder();
            hiddenLayerBuilder.nIn(i == 0 ? numFeatures : widthHiddenLayers);
            hiddenLayerBuilder.nOut(widthHiddenLayers);
            hiddenLayerBuilder.activation(Activation.TANH);
            listBuilder.layer(i, hiddenLayerBuilder.build());
        }

        RnnOutputLayer.Builder outputLayerBuilder = new RnnOutputLayer.Builder(LossFunctions.LossFunction.MCXENT);
        outputLayerBuilder.activation(Activation.SOFTMAX);
        outputLayerBuilder.nIn(widthHiddenLayers);
        outputLayerBuilder.nOut(numLabelClasses);
        listBuilder.layer(numHiddenLayers, outputLayerBuilder.build());

        listBuilder.pretrain(false);
        listBuilder.backprop(true);

        MultiLayerConfiguration conf = listBuilder.build();
        MultiLayerNetwork net = new MultiLayerNetwork(conf);
        net.init();
        net.setListeners(new ScoreIterationListener(50));

        log.info("Number of parameters in network: " + net.numParams());
        for( int i=0; i<net.getnLayers(); i++ ){
            log.info("Layer " + i + " nParams = " + net.getLayer(i).numParams());
        }
        return net;
    }

    public void generateTrainingInputData()
    {
        //Generate Training Data...
        boolean bForceRegenerateTrainingData = Boolean.parseBoolean(configReader.getProperty("forceRegenerateTrainingData"));
        if(bForceRegenerateTrainingData) {
            String symbol = configReader.getProperty("symbol");
            String rawDataSourceDirName = configReader.getProperty("rawDataSourceDir");
            File rawDataSourceDir = new File(rawDataSourceDirName);
            if (!rawDataSourceDir.exists()) {
                System.err.println("The raw data source directory doesn't exist: " + rawDataSourceDirName);
                return;
            }

            String[] rawSourceFileNames = rawDataSourceDir.list(new CSVFilenameFilter());
            log.info("The number of raw source files: " + rawSourceFileNames.length);
            for (String rawFileName : rawSourceFileNames)
                log.info(rawFileName);

            String mainSourceFileName = configReader.getProperty("mainSourceFileName");
            List<String> sourceFileNames = new ArrayList<>();
            //Make sure add the main source file first.
            sourceFileNames.add(rawDataSourceDirName + "/" + mainSourceFileName);

            for (String rawFileName : rawSourceFileNames) {
                if (mainSourceFileName.equals(rawFileName))
                    continue;
                sourceFileNames.add(rawDataSourceDirName + "/" + rawFileName);
                log.info("The support file will be loaded: " + rawFileName);
            }

            int numSequencePerGeneratedFile = Integer.parseInt(configReader.getProperty("numSequencePerGeneratedFile"));
            BasicLSTMDataGenerator.generateLSTMTrainingData2(symbol, sourceFileNames, numSequencePerGeneratedFile);
        }
    }

    public void trainAndValidate(MultiLayerNetwork net)
    {
        //Generate Training Data...
        boolean bForceRegenerateTrainingData = Boolean.parseBoolean(configReader.getProperty("forceRegenerateTrainingData"));
        if(bForceRegenerateTrainingData) {
        	DLUtils.generateMultiSymbolTrainingInputData(configReader);
        }
        
        int numCrossValidations = Integer.parseInt(configReader.getProperty("numCrossValidations"));
        int numEpochs = Integer.parseInt(configReader.getProperty("numEpochs"));
        int miniBatchSize = Integer.parseInt(configReader.getProperty("miniBatchSize"));

        if(null == net)
            net = buildNetworkModel();

        String rawDataDirName = configReader.getProperty("trainInputDirName");
        double trainDataPercentage = Double.parseDouble(configReader.getProperty("trainDataPercentage"));
        File rawDataDir = new File(rawDataDirName);
        if(!rawDataDir.exists()){
            System.err.println("The raw data directory doesn't exist: " + rawDataDirName);
            return;
        }

        int startIdx = 0;
        int endIdx = rawDataDir.list(new CSVFilenameFilter()).length - 1;
        int length = endIdx - startIdx + 1;
        int testStartIdx = (int) Math.round(length * trainDataPercentage);

        if(startIdx > endIdx || length < 1 || testStartIdx < 1 || testStartIdx > endIdx)
        {
            System.err.println("Wrong indexing calculation and buildTrainAndTestDataset function stopped.");
            return;
        }
        
     // ----- Load the training data -----
        DataSetIterator trainData = null;
        DataSetIterator testData = null;
        SequenceRecordReader trainFeatures = new CSVSequenceRecordReader();
        SequenceRecordReader testFeatures = new CSVSequenceRecordReader();
        SequenceRecordReader trainLabels = new CSVSequenceRecordReader();
        SequenceRecordReader testLabels = new CSVSequenceRecordReader();
        //Cross Validation Iterations.
        for(int idxCV = 0; idxCV < numCrossValidations; ++idxCV)
        {
            log.info("++++++++++++++++++++ Start Cross Validation iteration " + idxCV + " +++++++++++++++++++++++++++++\n");
            if(idxCV > 0)
            {
                DLUtils.shuffuleTrainingData(rawDataDir);
            }

            try {
                
                trainFeatures.initialize(new NumberedFileInputSplit(rawDataDir.getAbsolutePath() + "/%d.csv", 0, testStartIdx - 1));
                
                trainLabels.initialize(new NumberedFileInputSplit(rawDataDir.getAbsolutePath() + "/3/%d.csv", 0, testStartIdx - 1));

                trainData = new SequenceRecordReaderDataSetIterator(trainFeatures, trainLabels, miniBatchSize, labelClass.getNumLabels(), false
                        , SequenceRecordReaderDataSetIterator.AlignmentMode.ALIGN_END);
                
                //Same process as for the training data.
                testFeatures.initialize(new NumberedFileInputSplit(rawDataDir.getAbsolutePath() + "/%d.csv", testStartIdx, endIdx));
                
                testLabels.initialize(new NumberedFileInputSplit(rawDataDir.getAbsolutePath() + "/3/%d.csv", testStartIdx, endIdx));

                testData = new SequenceRecordReaderDataSetIterator(testFeatures, testLabels, miniBatchSize, labelClass.getNumLabels(), false
                        , SequenceRecordReaderDataSetIterator.AlignmentMode.ALIGN_END);
            } catch (IOException ioe)
            {
                ioe.printStackTrace();
                return;
            } catch (InterruptedException ie)
            {
                ie.printStackTrace();
                return;
            }

            //Use previously collected statistics to normalize on-the-fly. Each DataSet returned by 'trainData' iterator will be normalized
//            trainData.setPreProcessor(normalizer);
//            testData.setPreProcessor(normalizer);   //Note that we are using the exact same normalization process as the training data
            String str = "Test set evaluation at epoch %d: Accuracy = %.2f, F1 = %.2f";
            String str2 = "Test set evaluation at epoch %d: Precision = %.2f, Recall = %.2f";
            for (int i = 0; i < numEpochs; i++) {
                net.fit(trainData);
            } //Epoch Iterations.

            log.info("\nEvaluate model....\n");
            Evaluation eval = new Evaluation(labelClass.getNumLabels());
            int iterCount = 0;
            while(testData.hasNext()){
            	log.info("++++++++++++++++++++ Start Test Iteration #" + iterCount + " ++++++++++++++++++++++\n");
                DataSet t = testData.next();
                INDArray features = t.getFeatures();
                INDArray labels = t.getLabels();
                INDArray predicted = net.output(features,false);
                log.info("++++++++++++++++++++ predicted ++++++++++++++++++++++\n");
                log.info(DataUtils.printINDArray(predicted));
                log.info("++++++++++++++++++++ labeled ++++++++++++++++++++++\n");
                log.info(DataUtils.printINDArray(labels));
                log.info("++++++++++++++++++++ labeled ++++++++++++++++++++++\n");
                eval.eval(labels, predicted);
                log.info(String.format(str, iterCount, eval.accuracy(), eval.f1()));
                log.info(String.format(str2, iterCount, eval.precision(), eval.recall()));
                ++iterCount;
                log.info("++++++++++++++++++++ End Test Iteration #" + iterCount + " ++++++++++++++++++++++\n");
            }
            testData.reset();
            trainData.reset();
            log.info("++++++++++++++++++++ End Cross Validation iteration " + idxCV + " +++++++++++++++++++++++++++++\n");

        } //Cross Validation Iterations.

        log.info("----- CustomizedLSTMRunner Complete -----\n");
    }

    public void predict(MultiLayerNetwork net)
    {
        int numFeatures = DLUtils.detectNumFeaturesFromTrainingData(configReader);
        int numSequencePerGeneratedFile = Integer.parseInt(configReader.getProperty("numSequencePerGeneratedFile"));
        int numLabelClasses = labelClass.getNumLabels();

        String symbol = configReader.getProperty("symbol");
        String rawDataSourceDirName = configReader.getProperty("rawDataSourceDir");
        File rawDataSourceDir = new File(rawDataSourceDirName);
        if (!rawDataSourceDir.exists()) {
            System.err.println("The raw data source directory doesn't exist: " + rawDataSourceDirName);
            return;
        }

        String[] rawSourceFileNames = rawDataSourceDir.list(new CSVFilenameFilter());

        String mainSourceFileName = configReader.getProperty("mainSourceFileName");
        List<String> sourceFileNames = new ArrayList<>();
        //Make sure add the main source file first.
        sourceFileNames.add(Paths.get(rawDataSourceDir.getAbsolutePath(), mainSourceFileName).toString());

        for (String rawFileName : rawSourceFileNames) {
            if (mainSourceFileName.equals(rawFileName))
                continue;
            sourceFileNames.add(Paths.get(rawDataSourceDir.getAbsolutePath(), rawFileName).toString());
            log.info("The support file will be loaded: " + rawFileName);
        }

        BasicLSTMDataGenerator.generateLSTMPredictionData(symbol, sourceFileNames, numSequencePerGeneratedFile);


        String predictInputDirName = configReader.getProperty("predictInputDirName");
        File rawDataDir = new File(predictInputDirName);
        if(!rawDataDir.exists()){
            System.err.println("The raw data directory doesn't exist: " + predictInputDirName);
            return;
        }

        String[] predictFileNames = rawDataDir.list(new CSVFilenameFilter());

        if(null == net)
            net = buildNetworkModel();

        // clear current stance from the last example
//        net.rnnClearPreviousState();

        // put the first caracter into the rrn as an initialisation
        List<double[]> predictData = loadPredictionData(rawDataDir.getAbsolutePath() + "/" + predictFileNames[predictFileNames.length - 1], numSequencePerGeneratedFile);


        INDArray testInit = Nd4j.zeros(numFeatures);
        for(int idx = 0; idx < numFeatures; ++idx)
            testInit.putScalar(idx, predictData.get(0)[idx]);

        // run one step -> IMPORTANT: rnnTimeStep() must be called, not
        // output()
        // the output shows what the net thinks what should come next
        INDArray output = net.rnnTimeStep(testInit);

        StringBuffer sb = new StringBuffer("---------------- Prediction Result Distribution -----------------\n");
        // now the net should guess LEARNSTRING.length mor characters
        for (int jj = 1; jj < predictData.size(); jj++) {

            // first process the last output of the network to a concrete
            // neuron, the neuron with the highest output cas the highest
            // cance to get chosen
            double[] outputProbDistribution = new double[numLabelClasses];
            for (int k = 0; k < outputProbDistribution.length; k++) {
                outputProbDistribution[k] = output.getDouble(k);
            }
            sb.append(DataUtils.printArray(outputProbDistribution)).append("\n");

            // use the last output as input
            INDArray nextInput = Nd4j.zeros(numFeatures);
            for(int idx = 0; idx < numFeatures; ++idx)
                nextInput.putScalar(idx, predictData.get(jj)[idx]);

            output = net.rnnTimeStep(nextInput);

        }
        sb.append("---------------- Prediction Result Distribution -----------------");
        log.info(sb.toString());
    }

    private List<double[]> loadPredictionData(String predictionFileName, int numSequencePerGeneratedFile)
    {
        List<double[]> retList = new ArrayList<>(numSequencePerGeneratedFile+1);
        Path path = Paths.get(predictionFileName);
        try
        {
            List<String> strList = Files.readAllLines(path, Charset.defaultCharset());
            for(String line : strList)
            {
                String[] strDoubleArr = line.split(",");
                double[] dArr = new double[strDoubleArr.length];
                int idx = 0;
                for(String strDouble : strDoubleArr)
                {
                    dArr[idx] = Double.parseDouble(strDouble);
                    ++idx;
                }
                retList.add(dArr);
            }
        } catch (IOException e)
        {
            e.printStackTrace();
        }

        return retList;
    }

    public String printDataDistribution(RunnerConfigFileReader configReader)
    {
    	String dataDir = configReader.getProperty("trainInputDirName") + "/" + 3;
    	return "\nData Distribution: \n" + DataUtils.printArray(DataUtils.analyzeDataDistribution(dataDir, this.labelClass));
    }
    
    public static void main( String[] args ) throws Exception {
        RunnerConfigFileReader configReader = new RunnerConfigFileReader("src/com/byeautumn/wb/dl/BigPoolLSTMRunner.properties");
        log.info(configReader.printSelf());
        
        
//        DLUtils.generateMultiSymbolTrainingInputData(configReader);

        BigPoolLSTMRunner runner = new BigPoolLSTMRunner(configReader);
        log.info(runner.printDataDistribution(configReader));
//        runner.generateTrainingInputData();
//        MultiLayerNetwork net = runner.buildNetworkModel();
        runner.trainAndValidate(null);
//        runner.predict(net);
    }
}
