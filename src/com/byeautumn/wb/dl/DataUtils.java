package com.byeautumn.wb.dl;

import com.byeautumn.wb.data.CSVFilenameFilter;
import com.byeautumn.wb.output.ILabelClass;
import com.byeautumn.wb.output.OHLCSequentialTrainingData;
import com.byeautumn.wb.output.SequentialFlatRecord;

import org.apache.commons.io.FileUtils;
import org.jfree.util.Log;
import org.nd4j.linalg.api.ndarray.INDArray;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Created by qiangao on 5/27/2017.
 */
public class DataUtils {
    public static double[] analyzeDataDistribution(List<OHLCSequentialTrainingData> dataList, ILabelClass labelClass)
    {
        double[] ret = new double[labelClass.getNumLabels()];

        for(OHLCSequentialTrainingData data : dataList)
        {
            SequentialFlatRecord record = data.getLastLabeledRecord();
            int label = (int)record.getLabel();
            ret[label] += 1;
        }

        return ret;
    }

    public static double[] analyzeDataDistribution(String labelDataDir, ILabelClass labelClass)
    {
        double[] ret = new double[labelClass.getNumLabels()];

        File dir = new File(labelDataDir);
        //Assuming all are csv files...
        String[] labelFileNames = dir.list(new CSVFilenameFilter());
        for(String labelFileName : labelFileNames)
        {
        	File labelFile = new File(labelDataDir, labelFileName);
        	List<String> lines = null;
			try {
				lines = FileUtils.readLines(labelFile);
			} catch (IOException e) {
				e.printStackTrace();
				return ret;
			}
			
        	for(String line : lines)
        	{
        		String[] cols = line.split(",");
        		double label = Double.parseDouble(cols[cols.length - 1]);
        		if(!labelClass.isValid(label))
        		{
        			Log.warn("The label " + label + " is NOT valid in file " + labelFile.getAbsolutePath() + ". Skipped!");
        			continue;
        		}
        		
                ret[(int)label] += 1;
        	}
        }

        return ret;
    }
    
    public static String printArray(double[] arr)
    {
        StringBuffer sb = new StringBuffer();
        for(double d : arr)
            sb.append(d).append(",");

        return sb.toString();
    }

    public static String printINDArray(INDArray arr)
    {
        if(arr.rank() > 3)
            return "";
        if(arr.rank() == 3)
            return print3DINDArray(arr);
        else if(arr.rank() == 2)
            return print2DINDArray(arr);
        else
            return print1DINDArray(arr);
    }

    private static String print3DINDArray(INDArray arr)
    {
        int rank = arr.rank();
        if(rank != 3)
            return "";
        StringBuffer sb = new StringBuffer();
        for(int ii = 0; ii < arr.size(0); ++ii)
        {
            sb.append("Matrix #").append(ii).append(": \n");
            for(int jj = 0; jj < arr.size(1); ++jj)
            {
                for(int kk = 0; kk < arr.size(2); ++kk)
                {
                    sb.append(arr.getDouble(ii, jj, kk)).append(",");
                }
                sb.append("\n");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    private static String print2DINDArray(INDArray arr)
    {
        int rank = arr.rank();
        if(rank != 2)
            return "";
        StringBuffer sb = new StringBuffer();
        for(int ii = 0; ii < arr.size(0); ++ii) {
            for(int jj = 0; jj < arr.size(1); ++jj)
            {
                sb.append(arr.getDouble(ii, jj)).append(",");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private static String print1DINDArray(INDArray arr)
    {
        int rank = arr.rank();
        if(rank != 1)
            return "";
        StringBuffer sb = new StringBuffer();
        for(int ii = 0; ii < arr.size(0); ++ii) {
            sb.append(arr.getDouble(ii)).append(",");
        }
        return sb.toString();
    }
}
