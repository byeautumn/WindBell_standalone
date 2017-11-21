package com.byeautumn.wb.ta;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.byeautumn.wb.data.OHLCElement;
import com.byeautumn.wb.data.OHLCElementTable;
import com.byeautumn.wb.data.OHLCUtils;
import com.tictactec.ta.lib.Core;
import com.tictactec.ta.lib.MInteger;
import com.tictactec.ta.lib.RetCode;

public class MACatcher {
	private static final Logger log = LoggerFactory.getLogger(MACatcher.class);
	
	public double getLatestDEMA(List<OHLCElement> elemList, int maLength)
	{
		if(null == elemList || elemList.size() < 1 || maLength < 1)
		{
			log.error("Invalid input(s). Computation failed.");
			return Double.NaN;
		}
		
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
        double[] ma1Out = new double[TOTAL_PERIODS];
        MInteger ma1Begin = new MInteger();
        MInteger ma1Length = new MInteger();
        
		retCode = c.dema(0, closePrice.length - 1, closePrice, maLength, ma1Begin, ma1Length, ma1Out);
		if(retCode != RetCode.Success)
		{
			log.error("Calculation failed for MA length: " + maLength);
			return Double.NaN;
		}
		
		return ma1Out[ma1Length.value - 1];
	}
	
	public static void main(String[] args) {
		
		String sourceFileName = "resources/DoubleMAStrategy/NDX.csv";
		OHLCElementTable elemTable = OHLCUtils.readOHLCDataSourceFile(sourceFileName);
		MACatcher mac = new MACatcher();
		double maValue = mac.getLatestDEMA(elemTable.getOHCLElementsSortedByDate(), 2);
		
		System.out.println("Maving Average Value: " + maValue);

	}

}
