package com.byeautumn.wb.ta;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.byeautumn.wb.data.OHLCElement;
import com.byeautumn.wb.data.OHLCElementTable;
import com.byeautumn.wb.data.OHLCUtils;
import com.byeautumn.wb.dl.BasicLSTMRunner;
import com.tictactec.ta.lib.Core;
import com.tictactec.ta.lib.MInteger;
import com.tictactec.ta.lib.RetCode;

public class DoubleMAStrategy implements IBackTestStrategy {
	private static final Logger log = LoggerFactory.getLogger(DoubleMAStrategy.class);
	private List<OHLCElement> elemList;
	private int[] shortMALengthArr = {2, 3, 5, 8, 11, 13, 21};
	private int[] longMALengthArr = {5, 8, 13, 21, 27, 34, 40, 55, 61, 67, 74, 89, 101};
	private int shortIdx = 0;
	private int longIdx = -1;
	
	public DoubleMAStrategy(OHLCElementTable elemTable)
	{
		preprocess(elemTable);
	}
	
	void preprocess(OHLCElementTable elemTable)
	{
		elemList = elemTable.getOHCLElementsSortedByDate();
		
	}

	void process()
	{
		final int TOTAL_PERIODS = elemList.size();
		double[] closePrice = new double[TOTAL_PERIODS];

		int count = 0;
		for(OHLCElement elem : elemList)
		{
			closePrice[count] = elem.getCloseValue();
			++count;
		}
		
		Core c = new Core();
		RetCode retCode;
		int[] pair = getNextMALengthPair();
		while(null != pair)
		{
	        double[] shortMAOut = new double[TOTAL_PERIODS];
	        double[] longMAOut = new double[TOTAL_PERIODS];
	        MInteger shortBegin = new MInteger();
	        MInteger shortLength = new MInteger();
	        MInteger longBegin = new MInteger();
	        MInteger longLength = new MInteger();
	        
			log.info("Processing short MA length " + pair[0] + " and long MA length " + pair[1] + " ...");
			
			retCode = c.sma(0, closePrice.length - 1, closePrice, pair[0], shortBegin, shortLength, shortMAOut);
			if(retCode != RetCode.Success)
			{
				log.error("Calculation failed for MA length pair: " + pair[0] + " - " + pair[1]);
				continue;
			}
			
			retCode = c.sma(0, closePrice.length - 1, closePrice, pair[1], longBegin, longLength, longMAOut);
			if(retCode != RetCode.Success)
			{
				log.error("Calculation failed for MA length pair: " + pair[0] + " - " + pair[1]);
				continue;
			}
			
			int elemIdx = longBegin.value;
			boolean bInPosition = false;
			double totalProfit = 0.0;
			int startHoldIdx = 0;
			while(elemIdx < longLength.value)
			{
				while(shortMAOut[elemIdx] < longMAOut[elemIdx] && elemIdx < longLength.value)
				{
					++elemIdx;
				}
				
				if(!bInPosition)
				{
					bInPosition = true;
					startHoldIdx = elemIdx;
				}
				
				while(shortMAOut[elemIdx] >= longMAOut[elemIdx] && elemIdx < longLength.value)
				{						
					++elemIdx;
				}
				
				if(bInPosition)
				{
					totalProfit += closePrice[elemIdx] - closePrice[startHoldIdx];
					bInPosition = false;
				}
				
				if(elemIdx == longLength.value)
				{
					if(bInPosition)
					{
						log.info("Total profit is " + totalProfit + " for MA length pair: " + pair[0] + " - " + pair[1]);
					}
					break;
				}
				

			}

			
			pair = getNextMALengthPair();
		}
	}
	
	private int[] getNextMALengthPair()
	{
		if(shortIdx == shortMALengthArr.length - 1 && longIdx == longMALengthArr.length - 1)
		{
			log.info("No more MA length pair.");
			return null;
		}
		
		int[] retPair = new int[2];
		if(longIdx < longMALengthArr.length - 1)
		{
			retPair[0] = shortMALengthArr[shortIdx];
			retPair[1] = longMALengthArr[++longIdx];
		}
		else //longIdx == longMALengthArr.length - 1
		{
			retPair[0] = shortMALengthArr[++shortIdx];
			longIdx = 0;
			while(longMALengthArr[longIdx] <= shortMALengthArr[shortIdx])
				++longIdx;
			
			retPair[1] = longMALengthArr[longIdx];
		}
		
		return retPair;
	}
	
	public static void main(String[] args) {
		String sourceFileName = "resources/DoubleMAStrategy/SPX_Daily.csv";
		OHLCElementTable elemTable = OHLCUtils.readOHLCDataSourceFile(sourceFileName);
		DoubleMAStrategy dmas = new DoubleMAStrategy(elemTable);
		dmas.process();

	}
	
}
