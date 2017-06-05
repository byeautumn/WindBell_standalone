package com.byeautumn.wb.dl;

import com.byeautumn.wb.data.CSVFilenameFilter;
import com.byeautumn.wb.data.OHLCUtils;
import com.byeautumn.wb.output.BasicLSTMDataGenerator;
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
 * Created by qiangao on 5/24/2017.
 */
public class CustomizedLSTMRunner {
	private static final Logger log = LoggerFactory.getLogger(CustomizedLSTMRunner.class);
	
    public MultiLayerNetwork buildNetworkModel(RunnerConfigFileReader configReader)
    {
        int numLabelClasses = Integer.parseInt(configReader.getProperty("numLabelClasses"));
        int numFeatures = DLUtils.detectNumFeaturesFromTrainingData(configReader);
        int neuralSizeMultiplyer = Integer.parseInt(configReader.getProperty("neuralSizeMultiplyer"));
        int numHiddenLayers = Integer.parseInt(configReader.getProperty("numHiddenLayers"));
        int widthHiddenLayers = numFeatures * neuralSizeMultiplyer;

        NeuralNetConfiguration.Builder builder = new NeuralNetConfiguration.Builder();
        builder.seed(123)    //Random number generator seed for improved repeatability. Optional.
                .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT).iterations(1)
                .weightInit(WeightInit.XAVIER)
                .updater(Updater.NESTEROVS).momentum(0.6)
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

        System.out.println("Number of parameters in network: " + net.numParams());
        for( int i=0; i<net.getnLayers(); i++ ){
            System.out.println("Layer " + i + " nParams = " + net.getLayer(i).numParams());
        }
        return net;
    }

    public void generateTrainingInputData(RunnerConfigFileReader configReader)
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
            System.out.println("The number of raw source files: " + rawSourceFileNames.length);
            for (String rawFileName : rawSourceFileNames)
                System.out.println(rawFileName);

            String mainSourceFileName = configReader.getProperty("mainSourceFileName");
            List<String> sourceFileNames = new ArrayList<>();
            //Make sure add the main source file first.
            sourceFileNames.add(rawDataSourceDirName + "/" + mainSourceFileName);

            for (String rawFileName : rawSourceFileNames) {
                if (mainSourceFileName.equals(rawFileName))
                    continue;
                sourceFileNames.add(rawDataSourceDirName + "/" + rawFileName);
                System.out.println("The support file will be loaded: " + rawFileName);
            }

            int numSequencePerGeneratedFile = Integer.parseInt(configReader.getProperty("numSequencePerGeneratedFile"));
            BasicLSTMDataGenerator.generateLSTMTrainingData2(symbol, sourceFileNames, numSequencePerGeneratedFile);
        }
    }

    public void trainAndValidate(RunnerConfigFileReader configReader, MultiLayerNetwork net)
    {
        //Generate Training Data...
        boolean bForceRegenerateTrainingData = Boolean.parseBoolean(configReader.getProperty("forceRegenerateTrainingData"));
        if(bForceRegenerateTrainingData) {
            this.generateTrainingInputData(configReader);
        }

        int numCrossValidations = Integer.parseInt(configReader.getProperty("numCrossValidations"));
        int numEpochs = Integer.parseInt(configReader.getProperty("numEpochs"));
        int miniBatchSize = Integer.parseInt(configReader.getProperty("miniBatchSize"));

        if(null == net)
            net = buildNetworkModel(configReader);

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
        SequenceRecordReader trainFeatures = new CSVSequenceRecordReader();
        SequenceRecordReader testFeatures = new CSVSequenceRecordReader();

        //Cross Validation Iterations.
        for(int idxCV = 0; idxCV < numCrossValidations; ++idxCV)
        {
            System.out.println("++++++++++++++++++++ Start Cross Validation iteration " + idxCV + " +++++++++++++++++++++++++++++");
            if(idxCV > 0)
            {
                DLUtils.shuffuleTrainingData(rawDataDir);
            }

            try {
                trainFeatures.initialize(new NumberedFileInputSplit(rawDataDir.getAbsolutePath() + "/%d.csv", startIdx, testStartIdx - 1));
                testFeatures.initialize(new NumberedFileInputSplit(rawDataDir.getAbsolutePath() + "/%d.csv", testStartIdx, endIdx));
            } catch (IOException ioe)
            {
                ioe.printStackTrace();
                return;
            } catch (InterruptedException ie)
            {
                ie.printStackTrace();
                return;
            }

            int numFeatures = DLUtils.detectNumFeaturesFromTrainingData(configReader);
            int numLabelClasses = Integer.parseInt(configReader.getProperty("numLabelClasses"));
            DataSetIterator trainData = new SequenceRecordReaderDataSetIterator(trainFeatures, miniBatchSize, numLabelClasses, numFeatures, false);
            DataSetIterator testData = new SequenceRecordReaderDataSetIterator(testFeatures, miniBatchSize, numLabelClasses, numFeatures, false);

            //Normalization. Is it needed?
            //Normalize the training data
            DataNormalization normalizer = new NormalizerStandardize();
            normalizer.fit(trainData);              //Collect training data statistics
            trainData.reset();

            //Use previously collected statistics to normalize on-the-fly. Each DataSet returned by 'trainData' iterator will be normalized
            trainData.setPreProcessor(normalizer);
            testData.setPreProcessor(normalizer);   //Note that we are using the exact same normalization process as the training data
            String str = "Test set evaluation at epoch %d: Accuracy = %.2f, F1 = %.2f";
            String str2 = "Test set evaluation at epoch %d: Precision = %.2f, Recall = %.2f";
            for (int i = 0; i < numEpochs; i++) {
                net.fit(trainData);

                //Evaluate on the test set:
                Evaluation evaluation = net.evaluate(testData);
                System.out.println(String.format(str, i, evaluation.accuracy(), evaluation.f1()));
                System.out.println(String.format(str2, i, evaluation.precision(), evaluation.recall()));


                testData.reset();
                trainData.reset();
            } //Epoch Iterations.

            System.out.println("++++++++++++++++++++ End Cross Validation iteration " + idxCV + " +++++++++++++++++++++++++++++");
            System.out.println();

        } //Cross Validation Iterations.

        System.out.println("----- CustomizedLSTMRunner Complete -----");
    }

    public void predict(RunnerConfigFileReader configReader, MultiLayerNetwork net)
    {
        int numFeatures = DLUtils.detectNumFeaturesFromTrainingData(configReader);
        int numSequencePerGeneratedFile = Integer.parseInt(configReader.getProperty("numSequencePerGeneratedFile"));
        int numLabelClasses = Integer.parseInt((configReader.getProperty("numLabelClasses")));

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
            System.out.println("The support file will be loaded: " + rawFileName);
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
            net = buildNetworkModel(configReader);

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
            sb.append(printDoubleArray(outputProbDistribution)).append("\n");

            // use the last output as input
            INDArray nextInput = Nd4j.zeros(numFeatures);
            for(int idx = 0; idx < numFeatures; ++idx)
                nextInput.putScalar(idx, predictData.get(jj)[idx]);

            output = net.rnnTimeStep(nextInput);

        }
        sb.append("---------------- Prediction Result Distribution -----------------");
        System.out.println(sb.toString());
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

    private String printDoubleArray(double[] dArr)
    {
        StringBuffer sb = new StringBuffer();
        for(double d : dArr)
            sb.append(d).append(",");
        return sb.toString();
    }
    public static void main( String[] args ) throws Exception {
        RunnerConfigFileReader configReader = new RunnerConfigFileReader("../../WindBell/WindBell/src/com/byeautumn/wb/dl/CustomizedLSTMRunner.properties");
        System.out.println(configReader.printSelf());

        CustomizedLSTMRunner runner = new CustomizedLSTMRunner();
        runner.generateTrainingInputData(configReader);
        MultiLayerNetwork net = runner.buildNetworkModel(configReader);
        runner.trainAndValidate(configReader, net);
        runner.predict(configReader, net);


    }
}
