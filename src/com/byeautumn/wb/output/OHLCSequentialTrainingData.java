package com.byeautumn.wb.output;

import com.byeautumn.wb.data.OHLCElement;
import com.byeautumn.wb.data.OHLCElementTable;
import com.byeautumn.wb.data.OHLCUtils;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Created by qiangao on 5/17/2017.
 */
public class OHLCSequentialTrainingData {
	private static final Logger log = LoggerFactory.getLogger(OHLCSequentialTrainingData.class);
    private static final double MILLION_FACTOR = 0.000001;
    private List<SequentialFlatRecord> flatData;
    private ILabelClass labelClass = new LabelClass7();

    private OHLCSequentialTrainingData() {}
    
    //NOTE: this constructor assumes the first OHLCElementTable is the "main" table and the others will horizontally union with it.
    public OHLCSequentialTrainingData(List<OHLCElementTable> ohlcElementTables)
    {
        if(null == ohlcElementTables || ohlcElementTables.size() < 1)
        {
            System.err.println("Invalid input(s) for OHLCSequentialTrainingData generation.");
            return;
        }

        buildFlatData(ohlcElementTables);
    }

    public OHLCSequentialTrainingData(OHLCElementTable ohlcElementTable, boolean isForRegression, Date trancatedDate)
    {
        if(null == ohlcElementTable)
        {
            System.err.println("Invalid input(s) for OHLCSequentialTrainingData generation.");
            return;
        }

        buildFlatData(ohlcElementTable, isForRegression, trancatedDate);
    }

    public OHLCSequentialTrainingData(OHLCElementTable ohlcElementTable, boolean isForRegression)
    {
        this(ohlcElementTable, isForRegression, null);
    }

    public OHLCSequentialTrainingData(OHLCElementTable ohlcElementTable)
    {
        this(ohlcElementTable, false, null);
    }

    public static OHLCSequentialTrainingData createInstance(List<SequentialFlatRecord> flatData)
    {
        if(null == flatData)
        {
            System.err.println("The sequence length should be consistent. OHLCSequentialTrainingData generation failed.");
            return null;
        }

        OHLCSequentialTrainingData ret = new OHLCSequentialTrainingData();
        ret.flatData = flatData;

        return ret;
    }

    public List<OHLCSequentialTrainingData> split(int numSequence)
    {
        if(numSequence > this.flatData.size())
        {
            System.err.println();
            return null;
        }

        List<OHLCSequentialTrainingData> trainDataSplits = new ArrayList<>(flatData.size() - numSequence + 1);
        for(int idx = 0; idx <= flatData.size() - numSequence; ++idx)
        {
            List<SequentialFlatRecord> subFlatData = new ArrayList<>(numSequence);
            for(int secIdx = idx; secIdx < idx + numSequence; ++secIdx)
            {
                SequentialFlatRecord flatRecord = this.flatData.get(secIdx).clone();
                subFlatData.add(flatRecord);
            }

            OHLCSequentialTrainingData subTrainData = OHLCSequentialTrainingData.createInstance(subFlatData);
            trainDataSplits.add(subTrainData);
        }

        return trainDataSplits;
    }

    public OHLCSequentialTrainingData normalizeByFirstRecord()
    {
        if(null == this.flatData || this.flatData.isEmpty())
            return null;

        List<SequentialFlatRecord> normalizedRecordList = new ArrayList<>(this.flatData.size());
        SequentialFlatRecord baseRecord = this.flatData.get(0);
        for(SequentialFlatRecord record : this.flatData)
        {
            SequentialFlatRecord nRecord = record.normalizeValues(baseRecord);
            normalizedRecordList.add(nRecord);
        }

        return OHLCSequentialTrainingData.createInstance(normalizedRecordList);
    }

    private SequentialFlatRecord buildFlatRecord(OHLCElement currElem, OHLCElement nextElem, boolean isForRegression)
    {
        //Assume each OHLCElement has 5 values.
        int featureSize = 5;
        double[] flatValues = new double[featureSize];

        flatValues[0] = currElem.getOpenValue();
        flatValues[1] = currElem.getHighValue();
        flatValues[2] = currElem.getLowValue();
        flatValues[3] = currElem.getCloseValue();
        flatValues[4] = currElem.getVolumeValue();

        double label = Double.MIN_VALUE;
        if(null != nextElem)
        {
            double percentage = (0 == currElem.getCloseValue()) ? 0 : nextElem.getCloseValue() /currElem.getCloseValue();
            if(isForRegression)
            	label = percentage;
            else
            {
            	label = labelClass.getLabel(percentage - 1.0);
            	if(!labelClass.isValid(label))
        			log.error("The generated label is NOT valid: " + label);
            }
        }
        Date date = currElem.getDateValue();
        SequentialFlatRecord flatRecord = new SequentialFlatRecord(new Date(date.getTime()), flatValues, label, isForRegression);
        
        return flatRecord;
    }
    
    private void buildFlatData(OHLCElementTable table, boolean isForRegression, Date trancatedDate)
    {
        if(null == table || table.size() < 1)
        {
            System.err.println("The OHLCSequentialTrainingData instance is empty. Stop building flat data.");
            return;
        }

        if(null == flatData)
            flatData = new ArrayList<>(table.size());

        List<OHLCElement> elemList = table.getOHCLElementsSortedByDate();

        for (int timeSeriesIdx = 0; timeSeriesIdx < elemList.size(); ++timeSeriesIdx) {
            OHLCElement elem = elemList.get(timeSeriesIdx);
            Date date = elem.getDateValue();
            if(null != trancatedDate && trancatedDate.compareTo(date) > 0)
                continue;
            
            OHLCElement nextElem = null;
            if(timeSeriesIdx < elemList.size() - 1)
            	nextElem = elemList.get(timeSeriesIdx + 1);
            
            SequentialFlatRecord flatRecord = buildFlatRecord(elem, nextElem, isForRegression);
            flatData.add(flatRecord);
        }
    }

    private void buildFlatData(List<OHLCElementTable> tableList)
    {
    	buildFlatData(tableList, false);
    }
    
    private void buildFlatData(List<OHLCElementTable> tableList, boolean isForRegression)
    {
        if(null == tableList || tableList.isEmpty())
        {
            System.err.println("The OHLCSequentialTrainingData instance is empty. Stop building flat data.");
            return;
        }

        if(null == flatData)
            flatData = new ArrayList<>(tableList.get(0).size());

        //Assume each OHLCElement has 5 values.
        int feartureSizePerOHLCElement = 5;
        int featureSize = tableList.size() * feartureSizePerOHLCElement;
        OHLCElementTable mainTable = tableList.get(0);
        List<OHLCElement> mainElemList = mainTable.getOHCLElementsSortedByDate();

        for (int timeSeriesIdx = 0; timeSeriesIdx < mainElemList.size(); ++timeSeriesIdx) {
            OHLCElement elem = mainElemList.get(timeSeriesIdx);
            Date date = elem.getDateValue();
            double[] flatValues = new double[featureSize];
            if(tableList.size() > 1) {
                boolean bMissingMatch = false;
                //Note: idx starts from 1
                for (int idx = 1; idx < tableList.size(); ++idx) {
                    OHLCElementTable table = tableList.get(idx);
                    OHLCElement matchElem = table.getOHLCElement(date);
                    if (null == matchElem) {
                        bMissingMatch = true;
                        break;
                    }

                    flatValues[feartureSizePerOHLCElement * idx] = matchElem.getOpenValue();
                    flatValues[feartureSizePerOHLCElement * idx + 1] = matchElem.getHighValue();
                    flatValues[feartureSizePerOHLCElement * idx + 2] = matchElem.getLowValue();
                    flatValues[feartureSizePerOHLCElement * idx + 3] = matchElem.getCloseValue();
                    flatValues[feartureSizePerOHLCElement * idx + 4] = matchElem.getVolumeValue();

                }
                if (bMissingMatch)
                    continue;
            }
            flatValues[0] = elem.getOpenValue();
            flatValues[1] = elem.getHighValue();
            flatValues[2] = elem.getLowValue();
            flatValues[3] = elem.getCloseValue();
            flatValues[4] = elem.getVolumeValue();

            double label = Double.MIN_VALUE;
            if(timeSeriesIdx < mainElemList.size() - 1)
            {
                OHLCElement nextElem = mainElemList.get(timeSeriesIdx + 1);
                double percentage = (0 == elem.getCloseValue()) ? 0 : nextElem.getCloseValue() /elem.getCloseValue();
                if(isForRegression)
                	label = percentage;
                else
                {
                	label = labelClass.getLabel(percentage - 1.0);
                	if(!labelClass.isValid(label))
            			log.error("The generated label is NOT valid: " + label);
                }
            }

            SequentialFlatRecord flatRecord = new SequentialFlatRecord(new Date(date.getTime()), flatValues, label);
            flatData.add(flatRecord);
        }
    }

    public SequentialFlatRecord getLastLabeledRecord()
    {
        for(int idx = this.flatData.size() - 1; idx >= 0; --idx)
        {
            SequentialFlatRecord record = this.flatData.get(idx);
            if(labelClass.isValid(record.getLabel()))
                return record;
        }
        return null;
    }

    public String printSelfAsCSV()
    {
        return printSelfAsCSV(0, flatData.size() - 1, true);
    }

    public String printSelfAsCSV(int startIdx, int endIdx, boolean bExcludeUnlabeledRecord)
    {
        if(startIdx < 0 || endIdx < startIdx)
        {
            System.err.println("Invalid input(s).");
            return "";
        }

        StringBuffer sb = new StringBuffer();
        for(int idx = startIdx; idx <= endIdx; ++idx)
        {
            SequentialFlatRecord flatRecord = flatData.get(idx);
            if(bExcludeUnlabeledRecord) {
                if (!labelClass.isValid(flatRecord.getLabel()))
                    continue;
            }

            if(bExcludeUnlabeledRecord)
                sb.append(flatRecord.printValuesAndLabelWithDateInfoAsCSV());
            else
                sb.append(flatRecord.printValuesWithDateInfoAsCSV()); //In this case don't include label in the file at all.
            sb.append("\n");
        }
        return sb.toString();
    }

    public void generateTrainingCSVFile(String outputFileName) {
        generateCSVFile(outputFileName, false);
    }

    public void generateTrainingCSVFiles(String outputBaseDir, String fileName, int outputTimeStepLength, boolean isForRegression)
    {
        int numFeatures = this.flatData.get(0).getValuesSize();
        //Generate label files...
        for(int featureIdx = 0; featureIdx < numFeatures; ++featureIdx) {
            StringBuffer sb = new StringBuffer();
            for (int idx = outputTimeStepLength; idx < flatData.size() - 1; ++idx) {
                SequentialFlatRecord currRecord = flatData.get(idx);
//                SequentialFlatRecord nextRecord = flatData.get(idx + 1);
//                double currRegression = currRecord.getValueAt(featureIdx);
//                double nextRegression = nextRecord.getValueAt(featureIdx);
//                double percentage = 0.0 == currRegression ? 0.0 : nextRegression / currRegression;
//                if(isForRegression)
//                	sb.append(percentage).append("\n");
//                else
//                {
//                	int label = ()labelClass.getLabel(percentage - 1.0);
//                	
//                	sb.append(label).append("\n");
//                }
            	if(isForRegression)
            		sb.append(currRecord.getClass()).append("\n");
	            else
	            {
	            	int label = (int)currRecord.getLabel();	            	
	            	sb.append(label).append("\n");
	            }
            }
            String outputLabelFileName = Paths.get(outputBaseDir, "" + featureIdx, fileName).toString();
            generateTextFile(outputLabelFileName, sb.toString());
        }
        //Generate regression feature files...
        StringBuffer sbFeatures = new StringBuffer();
        int count = 0;
        for(SequentialFlatRecord record : flatData)
        {
            //Skip the last record since it has NO label...
            if(count >= flatData.size() - 1)
                break;

            sbFeatures.append(record.printValuesWithDateInfoAsCSV()).append("\n");
            ++count;
        }
        String outputFeaturesFileName = Paths.get(outputBaseDir, fileName).toString();
        generateTextFile(outputFeaturesFileName, sbFeatures.toString());
    }
    
    public void generateTextFile(String fileName, String content)
    {
        File outputFile = new File(fileName);
        if (outputFile.exists()) {
            if (!outputFile.delete()) {
                System.err.println("File " + outputFile.getAbsolutePath() + " cannot be deleted! File generation failed.");
                return;
            }
        }

        try
        {
            FileUtils.writeStringToFile(outputFile, content);
        } catch (IOException ioe)
        {
            ioe.printStackTrace();
        }
    }

    public void generateCSVFile(String outputFileName, boolean bForPrediction) {
        if (null == flatData) {
            System.err.println("The flat data is empty. CSV file generation failed.");
            return;
        }

        File outputFile = new File(outputFileName);
        if (outputFile.exists()) {
            if (!outputFile.delete()) {
                System.err.println("CSV file " + outputFile.getAbsolutePath() + " cannot be deleted! CSV file generation failed.");
                return;
            }
        }

        if(bForPrediction)
            generateTextFile(outputFileName, printSelfAsCSV(1, flatData.size() - 1, false));
        else
            generateTextFile(outputFileName, printSelfAsCSV());
    }

    public void generatePredictionCSVFile(String outputFileName)
    {
        generateCSVFile(outputFileName, true);
    }
}
