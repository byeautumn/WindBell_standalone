package com.byeautumn.wb.dl;

import com.byeautumn.wb.data.CSVFilenameFilter;
import com.byeautumn.wb.data.OHLCElementTable;
import com.byeautumn.wb.data.OHLCUtils;
import com.byeautumn.wb.output.CustomizedLSTMDataGenerator;
import com.byeautumn.wb.output.ILabelClass;
import com.byeautumn.wb.output.LabelClass7;
import com.byeautumn.wb.output.OHLCSequentialTrainingData;
import org.apache.commons.io.FileUtils;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.util.ModelSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Created by qiangao on 5/29/2017.
 */
public class DLUtils {
    private static final Logger log = LoggerFactory.getLogger(DLUtils.class);
    public static  MultiLayerNetwork loadNetworkModel(RunnerConfigFileReader configReader)
    {
        String networkSaveLocation = configReader.getProperty("networkSaveLocation");
        File networkSaveDir = new File(networkSaveLocation);
        if(!networkSaveDir.exists())
        {
            log.error("The given network model save directory doesn't exist.");
            return null;
        }

        String[] savedModelFileNames = networkSaveDir.list(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                if(name.endsWith(".zip"))
                    return true;
                return false;
            }
        });

        if(savedModelFileNames.length < 1)
        {
            log.error("There is NO valid network model exist under directory: " + networkSaveLocation);
            return null;
        }

        //Default is to load the last one (latest one hopefully).
        String networkModelFileName = savedModelFileNames[savedModelFileNames.length - 1];

        File modelFile = new File(networkSaveLocation, networkModelFileName);
        if(!modelFile.exists())
        {
            log.error("The saved network model file doesn't exist: " + modelFile.getAbsolutePath());
            return null;
        }

        //Load the model
        MultiLayerNetwork net = null;
        try {
            net = ModelSerializer.restoreMultiLayerNetwork(modelFile);
        } catch (IOException ioe)
        {
            log.error(ioe.getMessage());
            return null;
        }

        log.info("The saved network model has been successfully loaded: " + modelFile.getAbsolutePath());

        return net;
    }

    public static int detectNumFeaturesFromTrainingData(RunnerConfigFileReader configReader)
    {
        String trainInputDirName = configReader.getProperty("trainInputDirName");
        boolean isFeatureAndLabelInSameFile = Boolean.parseBoolean(configReader.getProperty("isFeatureAndLabelInSameFile"));
        File trainInputDir = new File(trainInputDirName);
        //Get the feature number by reading the one of the training csv file.
        String[] inputCSVFiles = trainInputDir.list(new CSVFilenameFilter());
        if(null == inputCSVFiles || inputCSVFiles.length < 1)
        {
            log.error("There is NO training input csv files in " + trainInputDir);
            return -1;
        }
        String csvSampleFileName = null;
        for(String csv : inputCSVFiles)
        {
            if(csv.endsWith(".csv"))
                csvSampleFileName = csv;
        }

        if(null == csvSampleFileName)
        {
            log.error("There is NO training input csv files in " + trainInputDir);
            return -1;
        }

        List<String> sampleLines = null;
        try {
            sampleLines = FileUtils.readLines(new File(trainInputDir, csvSampleFileName));
        } catch (IOException e) {
            e.printStackTrace();
        }
        if(null == sampleLines || sampleLines.isEmpty())
        {
            log.error("The sample csv file is empty:  " + csvSampleFileName);
            return -1;
        }
        //Pick the middle line...
        String sampleLine = sampleLines.get(sampleLines.size() / 2);
        String[] sampleValues = sampleLine.split(",");
        if(null == sampleValues || sampleValues.length < 2)
        {
            log.error("The sample line format of the sample csv file seems invalid:  " + sampleLine);
            return -1;
        }

        int numFeatures = isFeatureAndLabelInSameFile ? sampleValues.length - 1 : sampleValues.length;
        log.info("The detected numFeatures is: " + numFeatures);

        return numFeatures;
    }

    public static void generateTrainingInputData(RunnerConfigFileReader configReader)
    {
        //Generate Training Data...
        boolean bForceRegenerateTrainingData = Boolean.parseBoolean(configReader.getProperty("forceRegenerateTrainingData"));
        if(bForceRegenerateTrainingData) {
            String symbol = configReader.getProperty("symbol");
            List<String> sourceFileNames = getMainAndSupportiveFileNamesFromSourceDir(configReader);
            int numSequencePerGeneratedFile = Integer.parseInt(configReader.getProperty("numSequencePerGeneratedFile"));
            String outputDirName = configReader.getProperty("trainInputDirName");
            CustomizedLSTMDataGenerator.generateLSTMTrainingData2(symbol, sourceFileNames, outputDirName, numSequencePerGeneratedFile);
        }
    }

    private static List<String> getMainAndSupportiveFileNamesFromSourceDir(RunnerConfigFileReader configReader)
    {
        String rawDataSourceDirName = configReader.getProperty("rawDataSourceDir");
        File rawDataSourceDir = new File(rawDataSourceDirName);
        if (!rawDataSourceDir.exists()) {
            log.error("The raw data source directory doesn't exist: " + rawDataSourceDirName);
            return Collections.EMPTY_LIST;
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

        return sourceFileNames;
    }

    private static List<String> getRawFileNamesFromSourceDir(RunnerConfigFileReader configReader)
    {
        String rawDataSourceDirName = configReader.getProperty("rawDataSourceDir");
        File rawDataSourceDir = new File(rawDataSourceDirName);
        if (!rawDataSourceDir.exists()) {
            log.error("The raw data source directory doesn't exist: " + rawDataSourceDirName);
            return Collections.EMPTY_LIST;
        }

        String[] rawSourceFileNames = rawDataSourceDir.list(new CSVFilenameFilter());
        log.info("The number of raw source files: " + rawSourceFileNames.length);
        for (String rawFileName : rawSourceFileNames)
            log.info(rawFileName);

        List<String> sourceFileNames = new ArrayList<>();
        for (String rawFileName : rawSourceFileNames) {
            sourceFileNames.add(rawDataSourceDirName + "/" + rawFileName);
        }

        return sourceFileNames;
    }

    public static void generateMultiSymbolTrainingInputData(RunnerConfigFileReader configReader)
    {
        //Clean old data by deleting the source folder and create again ...
        String outputDirName = configReader.getProperty("trainInputDirName");
        cleanDirectory(new File(outputDirName));

        List<String> sourceFileNames = getRawFileNamesFromSourceDir(configReader);
        List<OHLCElementTable> ohlcTableList = new ArrayList<>(sourceFileNames.size());
        for(String sourceFileName : sourceFileNames)
        {
            OHLCElementTable ohlcTable = OHLCUtils.readOHLCDataSourceFile(sourceFileName);
            if(null == ohlcTable)
                continue;
            ohlcTableList.add(ohlcTable);
        }

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        Date trancatedDate = null;
        try {
            trancatedDate = dateFormat.parse(configReader.getProperty("inputDataNoOlderThan"));
        } catch (ParseException pe)
        {
            pe.printStackTrace();
        }
        int numSequencePerGeneratedFile = Integer.parseInt(configReader.getProperty("numSequencePerGeneratedFile"));
        int numSequenceBeforeLabeling = Integer.parseInt(configReader.getProperty("numSequenceBeforeLabeling"));
        boolean isForRegression = Boolean.parseBoolean(configReader.getProperty("isForRegression"));
        List<OHLCSequentialTrainingData>  dataList = new ArrayList<>();
        int count = 0;
        for(OHLCElementTable ohlcTable : ohlcTableList)
        {
            OHLCSequentialTrainingData data = new OHLCSequentialTrainingData(ohlcTable, isForRegression, trancatedDate);
            if(count == 0)
            	log.warn(data.printSelfAsCSV());
            List<OHLCSequentialTrainingData> pieceList = data.split(numSequencePerGeneratedFile);
            log.warn(pieceList.get(0).printSelfAsCSV());
            for(OHLCSequentialTrainingData piece : pieceList)
            {
                OHLCSequentialTrainingData normalizedPiece = piece.normalizeByFirstRecord();
                dataList.add(normalizedPiece);
            }

            if(count == 0)
                log.info(dataList.get(0).getLastLabeledRecord().printSelf());
            ++count;
        }
        log.info("dataList size: " + dataList.size());
        log.info(dataList.get(0).printSelfAsCSV());

        int pieceCount = 0;
        for(OHLCSequentialTrainingData trainData : dataList)
        {
            trainData.generateTrainingCSVFiles(outputDirName, "" + pieceCount + ".csv", numSequenceBeforeLabeling, isForRegression);
            ++pieceCount;
        }
        log.info("Total number of csv regression / label files generated: " + pieceCount);
        log.info(DataUtils.printArray(DataUtils.analyzeDataDistribution(dataList, new LabelClass7())));
    }

    public static void cleanDirectory(File dir)
    {
        if(dir.exists())
        {
            try {
                FileUtils.deleteDirectory(dir);
            } catch (IOException ioe)
            {
                ioe.printStackTrace();
            }
        }
        if(!dir.mkdirs())
        {
            log.error("Folder " + dir.getPath() + " cannot be created!");
            return;
        }
    }

    public static void shuffuleTrainingData(File trainDataDir)
    {
        if(null == trainDataDir)
        {
            log.error("The given training data directory is null.");
            return;
        }

        if(!trainDataDir.exists())
        {
            log.error("The training data directory doesn't exist: " + trainDataDir.getAbsolutePath());
            return;
        }

        String[] dataFileNames = trainDataDir.list(new CSVFilenameFilter());
        if(null == dataFileNames || dataFileNames.length < 1)
        {
            log.error("There is NO any training data in directory " + trainDataDir.getAbsolutePath());
            return;
        }

        Random random = new Random();
        int randomIdx = random.nextInt(dataFileNames.length);
        for(int idx = 0; idx < dataFileNames.length / 2; ++idx)
        {
            String fileName1 = dataFileNames[idx];
            String fileName2 = dataFileNames[randomIdx];
            if(fileName1.equals(fileName2))
                continue;

            swapFilesContent(trainDataDir, fileName1, fileName2);
            randomIdx = random.nextInt(dataFileNames.length);
        }

    }

    private static void swapFilesContent(File dir, String fileName1, String fileName2)
    {
        if(null == dir)
        {
            log.error("The given training data directory is null.");
            return;
        }

        if(!dir.exists())
        {
            log.error("The training data directory doesn't exist: " + dir.getAbsolutePath());
            return;
        }

        File file1 = new File(dir.getAbsolutePath(), fileName1);
        File file2 = new File(dir.getAbsolutePath(), fileName2);
        if(!file1.exists() || !file2.exists())
        {
            log.error("The files to be swapped don't exist: " + fileName1 + " or " + fileName2);
            return;
        }
        try {
            String temp = FileUtils.readFileToString(file1);
            FileUtils.writeStringToFile(file1, FileUtils.readFileToString(file2));
            FileUtils.writeStringToFile(file2, temp);
        } catch (IOException ioe)
        {
            ioe.printStackTrace();
        }
    }

    public static void saveNetwork(MultiLayerNetwork net, File modelFile, boolean bSaveUpdater)
    {
        //Where to save the network. Note: the file is in .zip format - can be opened externally
        //Updater: i.e., the state for Momentum, RMSProp, Adagrad etc. Save this if you want to train your network more in the future
        try {
            ModelSerializer.writeModel(net, modelFile, bSaveUpdater);
        } catch (IOException ioe)
        {
            log.error(ioe.getMessage());
        }
    }

    public static void saveNetwork(MultiLayerNetwork net, File modelFile)
    {
        saveNetwork(net, modelFile, true);
    }

    public static ILabelClass getLabelClassInstance(String labelClassName)
    {
    	return getClassInstance(labelClassName);
    }
    
    @SuppressWarnings("unchecked")
	public static <T> T getClassInstance(String className)
    {
    	T t = null;
    	Class<T> c = null;
		try {
			c = (Class<T>) Class.forName(className);
			t = c.newInstance();
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InstantiationException ie) {
			// TODO Auto-generated catch block
			ie.printStackTrace();
		} catch (IllegalAccessException iae) {
			// TODO Auto-generated catch block
			iae.printStackTrace();
		}
    	return t;
    }
    
    public static void main( String[] args ) throws Exception {
        RunnerConfigFileReader configReader = new RunnerConfigFileReader("../../WindBell/WindBell/src/com/byeautumn/wb/dl/RegressionLSTMRunner.properties");
        generateMultiSymbolTrainingInputData(configReader);
    }
}
