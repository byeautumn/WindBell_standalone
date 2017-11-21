package com.byeautumn.wb.ta;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.byeautumn.wb.data.OHLCElement;
import com.byeautumn.wb.output.OHLCSequentialTrainingData;

public class BackTestingStrategry {
	private static final Logger log = LoggerFactory.getLogger(BackTestingStrategry.class);
	protected SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy");
	protected double profit = Double.NaN;
	protected double aggregatedPercentageProfit = Double.NaN;
	protected double ratioOfSuccess = Double.NaN;
	protected List<SecurityPosition> winningTrades;
	protected List<SecurityPosition> losingTrades;
	protected StringBuffer sbReport;
	
	public double goBackTestForAggregatedPercentageProfit(List<OHLCElement> elemList, List<Integer> intParams, List<Double> doubleParams, List<Boolean> boolParams)
	{
		return goBackTestForAggregatedPercentageProfit(elemList, intParams, doubleParams, boolParams, false);
	}
	
	protected double goBackTestForAggregatedPercentageProfit(List<OHLCElement> elemList, List<Integer> intParams, List<Double> doubleParams, List<Boolean> boolParams, boolean isReplay)
	{
		if(null == elemList || elemList.size() < 1) 
		{
			log.error("Invalid input(s). Computation failed.");
			return Double.MIN_VALUE;
		}
		
		if(Double.isNaN(this.profit) || Double.isNaN(this.ratioOfSuccess))
			goBackTest(elemList, intParams, doubleParams, boolParams, isReplay);
		
		return Double.isNaN(this.ratioOfSuccess) ? Double.MIN_VALUE : this.aggregatedPercentageProfit;
	}
	
	public double goBackTestForRatioOfSuccess(List<OHLCElement> elemList, List<Integer> intParams, List<Double> doubleParams, List<Boolean> boolParams)
	{
		return goBackTestForRatioOfSuccess(elemList, intParams, doubleParams, boolParams, false);
	}
	
	protected double goBackTestForRatioOfSuccess(List<OHLCElement> elemList, List<Integer> intParams, List<Double> doubleParams, List<Boolean> boolParams, boolean isReplay)
	{
		if(null == elemList || elemList.size() < 1) 
		{
			log.error("Invalid input(s). Computation failed.");
			return Double.MIN_VALUE;
		}
		
		if(Double.isNaN(this.profit) || Double.isNaN(this.ratioOfSuccess))
			goBackTest(elemList, intParams, doubleParams, boolParams, isReplay);
		
		return Double.isNaN(this.ratioOfSuccess) ? Double.MIN_VALUE : this.ratioOfSuccess;
	}
	
	public double goBackTestForWeightedProfit(List<OHLCElement> elemList, List<Integer> intParams, List<Double> doubleParams, List<Boolean> boolParams)
	{
		return goBackTestForWeightedProfit(elemList, intParams, doubleParams, boolParams, false);
	}
	
	protected double goBackTestForWeightedProfit(List<OHLCElement> elemList, List<Integer> intParams, List<Double> doubleParams, List<Boolean> boolParams, boolean isReplay)
	{
		if(null == elemList || elemList.size() < 1) 
		{
			log.error("Invalid input(s). Computation failed.");
			return Double.MIN_VALUE;
		}
		
		if(Double.isNaN(this.profit) || Double.isNaN(this.ratioOfSuccess))
			goBackTest(elemList, intParams, doubleParams, boolParams, isReplay);
		
		return (Double.isNaN(this.profit) || Double.isNaN(this.ratioOfSuccess)) ? Double.MIN_VALUE : this.ratioOfSuccess * this.profit / 100.0;
	}
	
	public double goBackTestForPercentageProfit(List<OHLCElement> elemList, List<Integer> intParams, List<Double> doubleParams, List<Boolean> boolParams)
	{
		return goBackTestForPercentageProfit(elemList, intParams, doubleParams, boolParams, false);
	}
	
	protected double goBackTestForPercentageProfit(List<OHLCElement> elemList, List<Integer> intParams, List<Double> doubleParams, List<Boolean> boolParams, boolean isReplay)
	{
		if(null == elemList || elemList.size() < 1) 
		{
			log.error("Invalid input(s). Computation failed.");
			return Double.MIN_VALUE;
		}
		
		if(Double.isNaN(this.profit) || Double.isNaN(this.ratioOfSuccess))
			goBackTest(elemList, intParams, doubleParams, boolParams, isReplay);
		
		return getTotalPercentageProfit();
	}
	
	public double goBackTestForProfits(List<OHLCElement> elemList, List<Integer> intParams, List<Double> doubleParams, List<Boolean> boolParams)
	{
		return goBackTestForProfits(elemList, intParams, doubleParams, boolParams, false);
	}
	
	protected double goBackTestForProfits(List<OHLCElement> elemList, List<Integer> intParams, List<Double> doubleParams, List<Boolean> boolParams, boolean isReplay)
	{
		if(null == elemList || elemList.size() < 1)
		{
			log.error("Invalid input(s). Computation failed.");
			return Double.MIN_VALUE;
		}
		
		
		if(Double.isNaN(this.profit) || Double.isNaN(this.ratioOfSuccess))
			goBackTest(elemList, intParams, doubleParams, boolParams, isReplay);
		
		return Double.isNaN(this.profit) ? Double.MIN_VALUE : this.profit;
	}
	
	protected void goBackTest(List<OHLCElement> elemList, List<Integer> intParams, List<Double> doubleParams, List<Boolean> boolParams, boolean isReplay)
	{
		log.error("This function is NOT implemented in this class, sub classes should have their own implementations.");
	}
	
	protected double getTotalPercentageProfit()
	{
		return getWinningPercentageProfit() + getLosingPercentageProfit();
	}
	
	protected double getPercentageProfit(List<SecurityPosition> trades)
	{
		double totalPercentageProfit = Double.MIN_VALUE;
		if(null != trades)
		{
			for(SecurityPosition sp : trades)
			{
				double currPercertageProfit = sp.getProfit() / sp.getOpentTradeUnit().getTradePrice() * 100.0;
				totalPercentageProfit += currPercertageProfit;
			}
		}
		
		return totalPercentageProfit;
	}
	
	protected double getWinningPercentageProfit()
	{		
		return getPercentageProfit(this.winningTrades);
	}
	
	protected double getLosingPercentageProfit()
	{		
		return getPercentageProfit(this.losingTrades);
	}
	
	protected void outputSortedLosingTrades(String fileName)
	{
		outputSortedTrades(fileName, this.losingTrades);
	}
	
	protected void outputSortedWinningTrades(String fileName)
	{
		outputSortedTrades(fileName, this.winningTrades);
	}
	
	private void outputSortedTrades(String fileName, List<SecurityPosition> trades)
	{
		if(null != trades && trades.size() > 0)
		{
			Collections.sort(trades, this.new SortByProfits());
			StringBuffer sb = new StringBuffer();
			for(SecurityPosition sp : trades)
			{
				sb.append(sp.printSelf());
			}

			OHLCSequentialTrainingData.generateTextFile(fileName, sb.toString());
			
			int listSize = trades.size();
			if(listSize % 2 == 0)
			{
				log.debug("Median trade(s): \n" + trades.get(listSize / 2).printSelf() + trades.get(listSize / 2 - 1).printSelf());
			}
			else
			{
				log.debug("Median trade(s): \n" + trades.get(listSize / 2).printSelf());
			}
		}
	}
	
	public class SortByProfits implements Comparator<SecurityPosition>
	{

		@Override
		public int compare(SecurityPosition o1, SecurityPosition o2) {
			if(o1.getProfit() < o2.getProfit())
				return -1;	
			if(o1.getProfit() > o2.getProfit())
				return 1;
			return 0;
		}

	}
}
