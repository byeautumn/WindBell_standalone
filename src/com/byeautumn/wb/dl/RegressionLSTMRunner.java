package com.byeautumn.wb.dl;

import com.byeautumn.wb.data.CSVFilenameFilter;
import com.byeautumn.wb.output.BasicLSTMDataGenerator;
import org.datavec.api.records.reader.SequenceRecordReader;
import org.datavec.api.records.reader.impl.csv.CSVSequenceRecordReader;
import org.datavec.api.split.NumberedFileInputSplit;
import org.deeplearning4j.datasets.datavec.SequenceRecordReaderDataSetIterator;
import org.deeplearning4j.eval.RegressionEvaluation;
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
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.NDArrayIndex;
import org.nd4j.linalg.lossfunctions.LossFunctions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by qiangao on 5/24/2017.
 */
public class RegressionLSTMRunner {
    private static final Logger log = LoggerFactory.getLogger(RegressionLSTMRunner.class);

    public MultiLayerNetwork buildNetworkModel(RunnerConfigFileReader configReader)
    {
        int numLabelClasses = Integer.parseInt(configReader.getProperty("numLabelClasses"));
        int numFeatures = DLUtils.detectNumFeaturesFromTrainingData(configReader);
        int neuralSizeMultiplyer = Integer.parseInt(configReader.getProperty("neuralSizeMultiplyer"));
        int numHiddenLayers = Integer.parseInt(configReader.getProperty("numHiddenLayers"));
        int widthHiddenLayers = numFeatures * neuralSizeMultiplyer;

//        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
//                .seed(140)
//                .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
//                .iterations(1)
//                .weightInit(WeightInit.XAVIER)
//                .updater(Updater.NESTEROVS).momentum(0.9)
//                .learningRate(0.15)
//                .list()
//                .layer(0, new GravesLSTM.Builder().activation(Activation.TANH).nIn(numOfVariables).nOut(10)
//                        .build())
//                .layer(1, new RnnOutputLayer.Builder(LossFunctions.LossFunction.MSE)
//                        .activation(Activation.IDENTITY).nIn(10).nOut(numOfVariables).build())
//                .build();
//
//        MultiLayerNetwork net = new MultiLayerNetwork(conf);

        NeuralNetConfiguration.Builder builder = new NeuralNetConfiguration.Builder();
        builder.seed(123)    //Random number generator seed for improved repeatability. Optional.
                .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT).iterations(1)
                .weightInit(WeightInit.XAVIER)
                .updater(Updater.NESTEROVS).momentum(0.9)
                .learningRate(0.01)
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

        RnnOutputLayer.Builder outputLayerBuilder = new RnnOutputLayer.Builder(LossFunctions.LossFunction.MSE);
        outputLayerBuilder.activation(Activation.IDENTITY);
        outputLayerBuilder.nIn(widthHiddenLayers);
        outputLayerBuilder.nOut(1); //in csv files
        listBuilder.layer(numHiddenLayers, outputLayerBuilder.build());

        listBuilder.pretrain(false);
        listBuilder.backprop(true);

        MultiLayerConfiguration conf = listBuilder.build();
        MultiLayerNetwork net = new MultiLayerNetwork(conf);
        net.init();
        net.setListeners(new ScoreIterationListener(100));

        log.info("Number of parameters in network: " + net.numParams());
        for( int i=0; i<net.getnLayers(); i++ ){
            log.info("Layer " + i + " nParams = " + net.getLayer(i).numParams());
        }
        return net;
    }

    public void trainAndValidate(RunnerConfigFileReader configReader, MultiLayerNetwork net)
    {
        //Generate Training Data...
        int numCrossValidations = Integer.parseInt(configReader.getProperty("numCrossValidations"));
        int numEpochs = Integer.parseInt(configReader.getProperty("numEpochs"));
        int miniBatchSize = Integer.parseInt(configReader.getProperty("miniBatchSize"));

        if(null == net)
            net = buildNetworkModel(configReader);

        String rawDataDirName = configReader.getProperty("trainInputDirName");
        double trainDataPercentage = Double.parseDouble(configReader.getProperty("trainDataPercentage"));
        File rawDataDir = new File(rawDataDirName);
        if(!rawDataDir.exists()){
            log.error("The raw data directory doesn't exist: " + rawDataDirName);
            return;
        }

        String[] allFileNames = rawDataDir.list(new CSVFilenameFilter());
        int startIdx = 0;
        int endIdx = allFileNames.length - 1;
        int length = endIdx - startIdx + 1;
        int testStartIdx = (int) Math.round(length * trainDataPercentage);

        if(startIdx > endIdx || length < 1 || testStartIdx < 1 || testStartIdx > endIdx)
        {
            log.error("Wrong indexing calculation and buildTrainAndTestDataset function stopped.");
            return;
        }

        //Cross Validation Iterations.
        for(int idxCV = 0; idxCV < numCrossValidations; ++idxCV)
        {
            log.info("++++++++++++++++++++ Start Cross Validation iteration " + idxCV + " +++++++++++++++++++++++++++++");
            if(idxCV > 0)
            {
                DLUtils.shuffuleTrainingData(rawDataDir);
            }

            // ----- Load the training data -----
            DataSetIterator trainData = null;
            DataSetIterator testData = null;
            try {
                SequenceRecordReader trainFeatures = new CSVSequenceRecordReader();
                trainFeatures.initialize(new NumberedFileInputSplit(rawDataDir.getAbsolutePath() + "/%d.csv", 0, testStartIdx - 1));
                SequenceRecordReader trainLabels = new CSVSequenceRecordReader();
                trainLabels.initialize(new NumberedFileInputSplit(rawDataDir.getAbsolutePath() + "/3/%d.csv", 0, testStartIdx - 1));

                trainData = new SequenceRecordReaderDataSetIterator(trainFeatures, trainLabels, miniBatchSize, -1, true
                        , SequenceRecordReaderDataSetIterator.AlignmentMode.ALIGN_END);
                //Same process as for the training data.
                SequenceRecordReader testFeatures = new CSVSequenceRecordReader();
                testFeatures.initialize(new NumberedFileInputSplit(rawDataDir.getAbsolutePath() + "/%d.csv", testStartIdx, endIdx));
                SequenceRecordReader testLabels = new CSVSequenceRecordReader();
                testLabels.initialize(new NumberedFileInputSplit(rawDataDir.getAbsolutePath() + "/3/%d.csv", testStartIdx, endIdx));

                testData = new SequenceRecordReaderDataSetIterator(testFeatures, testLabels, miniBatchSize, -1, true
                        , SequenceRecordReaderDataSetIterator.AlignmentMode.ALIGN_END);
            } catch(IOException ioe)
            {
                ioe.printStackTrace();
            } catch(InterruptedException ie)
            {
                ie.printStackTrace();
            }

//            DataSetIterator trainData = new MaskableLSTMDataSetIterator(rawDataDirName, trainFileNames, miniBatchSize, new LabelClass7());
//            DataSetIterator testData = new MaskableLSTMDataSetIterator(rawDataDirName, testFileNames, miniBatchSize, new LabelClass7());

            //Increase numEpochs each iteration of cross validation
            numEpochs *=(idxCV + 1);
            for (int i = 0; i < numEpochs; i++) {
                net.fit(trainData);

//                trainData.reset();
                log.info("Epoch " + i + " complete. Time series evaluation:");
            }
            RegressionEvaluation evaluation = new RegressionEvaluation(2);
            //Run evaluation. This is on 25k reviews, so can take some time
            int count = 0;
            while (testData.hasNext()) {
                DataSet t = testData.next();
                INDArray features = t.getFeatureMatrix();
                INDArray lables = t.getLabels();
                INDArray predicted = net.output(features, true);
                int nTimeStep = predicted.size(2);
                if(count < 3) {
                    log.info("TestData Iteration " + count);
                    log.info("++++++++++++++++++++++ Predicted ++++++++++++++++++++++++++");
                    log.info("predict rank: " + predicted.rank());
                    log.info(DataUtils.printINDArray(predicted.get(NDArrayIndex.point(0), NDArrayIndex.all(), NDArrayIndex.point(nTimeStep - 1))));
                    log.info("++++++++++++++++++++++ Predicted ++++++++++++++++++++++++++");
                    log.info("++++++++++++++++++++++ labels ++++++++++++++++++++++++++");
                    log.info("lables rank: " + lables.rank());
                    log.info(DataUtils.printINDArray(lables));
                    log.info("++++++++++++++++++++++ labels ++++++++++++++++++++++++++");
                    evaluation.evalTimeSeries(lables, predicted);
                }
                ++count;
            }
            log.info(evaluation.stats());

//            testData.reset();

//            String str = "Test set evaluation at epoch %d: Accuracy = %.2f, F1 = %.2f";
//            String str2 = "Test set evaluation at epoch %d: Precision = %.2f, Recall = %.2f";
//            for (int i = 0; i < numEpochs; i++) {
//                net.fit(trainData);
//
////                //Evaluate on the test set:
////                Evaluation evaluation = net.evaluate(testData);
////                log.info(String.format(str, i, evaluation.accuracy(), evaluation.f1()));
////                log.info(String.format(str2, i, evaluation.precision(), evaluation.recall()));
//                Evaluation evaluation = new Evaluation();
//                int count = 0;
//                while (testData.hasNext()) {
//                    DataSet t = testData.next();
//                    INDArray features = t.getFeatureMatrix();
//                    INDArray lables = t.getLabels();
//                    INDArray outMask = t.getLabelsMaskArray();
//                    INDArray predicted = net.output(features, false);
////                    if(count == 0)
////                    {
////                        int nTimeStep = predicted.size(2);
////                        log.info("TestData Iteration " + count);
////                        log.info("++++++++++++++++++++++ Predicted ++++++++++++++++++++++++++");
////                        log.info("predict rank: " + predicted.rank());
////                        log.info(DataUtils.printINDArray(predicted.get(NDArrayIndex.point(0), NDArrayIndex.all(), NDArrayIndex.point(nTimeStep - 1))));
////                        log.info("++++++++++++++++++++++ Predicted ++++++++++++++++++++++++++");
////                        log.info("++++++++++++++++++++++ labels ++++++++++++++++++++++++++");
////                        log.info("lables rank: " + lables.rank());
////                        log.info(DataUtils.printINDArray(lables));
////                        log.info("++++++++++++++++++++++ labels ++++++++++++++++++++++++++");
////                    }
//
//                    evaluation.evalTimeSeries(lables, predicted, outMask);
//                    ++count;
//                }
//                log.info(String.format(str, i, evaluation.accuracy(), evaluation.f1()));
//                log.info(String.format(str2, i, evaluation.precision(), evaluation.recall()));
//                testData.reset();
//                trainData.reset();
//            } //Epoch Iterations.

            log.info("++++++++++++++++++++ End Cross Validation iteration " + idxCV + " +++++++++++++++++++++++++++++");

            //Save the network model
            boolean bSaveModel = Boolean.parseBoolean(configReader.getProperty("saveModel"));
            if(bSaveModel)
            {
                String networkSaveLocation = configReader.getProperty("networkSaveLocation");
                Date now = new Date();
                File savedModel = new File(networkSaveLocation, String.format("RegressionLSTMRunner_%s_%d.zip", new SimpleDateFormat("yyyyMMdd-hhmm").format(now), idxCV));
                DLUtils.saveNetwork(net, savedModel);
                log.info("Network model %s has been saved.", savedModel.getAbsolutePath());
            }

            net.clear();

        } //Cross Validation Iterations.

        log.info("----- CustomizedLSTMRunner Complete -----");
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
            log.error("The raw data source directory doesn't exist: " + rawDataSourceDirName);
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
            log.error("The raw data directory doesn't exist: " + predictInputDirName);
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

    public static void main( String[] args ) throws Exception {
//        org.apache.log4j.Logger logger4j = org.apache.log4j.Logger.getRootLogger();
//        logger4j.setLevel(org.apache.log4j.Level.toLevel("ALL"));

        RunnerConfigFileReader configReader = new RunnerConfigFileReader("../WindBell/src/com/byeautumn/wb/dl/RegressionLSTMRunner.properties");
        log.info(configReader.printSelf());

        RegressionLSTMRunner runner = new RegressionLSTMRunner();

//        DLUtils.generateTrainingInputData(configReader);
//        DLUtils.generateMultiSymbolTrainingInputData(configReader);
//        MultiLayerNetwork net = runner.buildNetworkModel(configReader);
//        runner.trainAndValidate(configReader, net);
//        runner.predict(configReader, net);


    }
}
