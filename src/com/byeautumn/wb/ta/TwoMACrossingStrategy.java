package com.byeautumn.wb.ta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.byeautumn.wb.data.OHLCElement;
import com.byeautumn.wb.data.OHLCElementTable;
import com.byeautumn.wb.data.OHLCUtils;
import com.byeautumn.wb.output.OHLCSequentialTrainingData;
import com.tictactec.ta.lib.Core;
import com.tictactec.ta.lib.MInteger;
import com.tictactec.ta.lib.RetCode;

public class TwoMACrossingStrategy extends BackTestingStrategry{
	private static final Logger log = LoggerFactory.getLogger(TwoMACrossingStrategy.class);

	protected void goBackTest(List<OHLCElement> elemList, List<Integer> intParams, List<Double> doubleParams, List<Boolean> boolParams, boolean isReplay)
	{
		if(null == elemList || elemList.size() < 1 || null == intParams || intParams.size() != 2 || null == boolParams || boolParams.size() != 1)
		{
			log.error("Invalid input(s). Computation failed.");
			return;
		}
		
		int maLength1 = intParams.get(0);
		int maLength2 = intParams.get(1);
		double stopWin = (null == doubleParams || doubleParams.size() < 1) ? Double.NaN : doubleParams.get(0);
		double stopLoss = (null == doubleParams || doubleParams.size() < 2) ? Double.NaN : doubleParams.get(1);
		boolean isLong = boolParams.get(0);
		
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
        double[] ma2Out = new double[TOTAL_PERIODS];
        MInteger ma1Begin = new MInteger();
        MInteger ma1Length = new MInteger();
        MInteger ma2Begin = new MInteger();
        MInteger ma2Length = new MInteger();
        
		retCode = c.dema(0, closePrice.length - 1, closePrice, maLength1, ma1Begin, ma1Length, ma1Out);
		if(retCode != RetCode.Success)
		{
			log.error("Calculation failed for MA length pair: " + maLength1 + " - " + maLength2);
			return ;
		}
		
		retCode = c.sma(0, closePrice.length - 1, closePrice, maLength2, ma2Begin, ma2Length, ma2Out);
		if(retCode != RetCode.Success)
		{
			log.error("Calculation failed for MA length pair: " + maLength1 + " - " + maLength2);
			return ;
		}
		
		int startIdx = Math.max(ma1Begin.value, ma2Begin.value);
		int maxIdx = elemList.size() - 1;
		int winTradeCount = 0;
		double winProfits = 0.0;
		int loseTradeCount = 0;
		double loseProfits = 0.0;
		this.profit = 0.0;
		this.aggregatedPercentageProfit = 100.0;
		SecurityPosition sp = null;
		
		if(isReplay && null == this.sbReport)
		{
			this.sbReport = new StringBuffer();
		}
		
		double targetWinPrice = Double.MAX_VALUE;
		double targetLossPrice = Double.MIN_VALUE;
		for(int elemIdx = startIdx+1; elemIdx <= maxIdx; ++elemIdx)
		{
			double ma1Curr = ma1Out[elemIdx-ma1Begin.value];
			double ma2Curr = ma2Out[elemIdx-ma2Begin.value];
			OHLCElement currElem = elemList.get(elemIdx);
			double currClosePrice = currElem.getCloseValue();
			
			if(isReplay)
				this.sbReport.append(dateFormat.format(currElem.getDateValue())).append(": ").append(currClosePrice).append(" - ").append(ma1Curr).append(" | ").append(ma2Curr).append("    ");
			
			if(null == sp || !sp.isOpen())
			{
				double ma1Prev = ma1Out[elemIdx-ma1Begin.value-1];
				double ma2Prev = ma2Out[elemIdx-ma2Begin.value-1];
				if(ma1Prev < ma2Prev && ma1Curr >= ma2Curr)
				{
					if(isReplay)
					{
						log.debug("Open trade at price " + currElem.getCloseValue() + " on " + dateFormat.format(currElem.getDateValue()) + "; current profit so far is " + profit + ".");
						this.sbReport.append("-->");
					}
					
					if(!Double.isNaN(stopWin) && 0.0 != stopWin)
						targetWinPrice = currClosePrice * (1.0 + stopWin);
					if(!Double.isNaN(stopLoss) && 0.0 != stopLoss)
						targetLossPrice = currClosePrice * (1.0 - stopLoss);
					
					sp = SecurityPosition.open(new SecurityTradeUnit(currElem.getDateValue(), currElem.getCloseValue(), isLong));
					
				}
			}
			else //the position is open
			{
				double realClosePrice = Double.NaN;
				if(ma1Curr < ma2Curr)
				{
					realClosePrice = currClosePrice;

				}
				if(currClosePrice >= targetWinPrice)
				{
					realClosePrice = targetWinPrice;
				}
				else if(currClosePrice <= targetLossPrice)
				{
					realClosePrice = targetLossPrice;
				}
				if(!Double.isNaN(realClosePrice))
				{
					sp.close(new SecurityTradeUnit(currElem.getDateValue(), realClosePrice, !isLong));
					double currProfit = sp.getProfit();
					if(currProfit >0.0)
					{
						++winTradeCount;
						winProfits +=currProfit;
						if(null == winningTrades)
							winningTrades = new ArrayList<>();
						winningTrades.add(sp);
					}
					else
					{
						++loseTradeCount;
						loseProfits += currProfit;
						if(null == losingTrades)
							losingTrades = new ArrayList<>();
						this.losingTrades.add(sp);
					}
					
					this.profit += currProfit;
					this.aggregatedPercentageProfit *= 1.0 + sp.getPercentageProfit() / 100.0;
					
					if(isReplay)
					{
						log.debug("Close trade at price " + realClosePrice + " on " + dateFormat.format(currElem.getDateValue()) + " with profit of " + currProfit + "; current profit so far is " + this.profit + ".");
						this.sbReport.append("<--    ").append(currProfit);
					}
					
				}

			}			
			if(isReplay)
			{
				this.sbReport.append("\n");
			}
		}
		
		this.ratioOfSuccess = (winTradeCount < 1 && loseTradeCount < 1) ? Double.MIN_VALUE : (double)winTradeCount / (double)(winTradeCount + loseTradeCount) * 100.0;
		
		if(isReplay)
		{
			log.debug("Win trades: " + winTradeCount + "; Lose trades " + loseTradeCount + "; total Ratio of success: " + this.ratioOfSuccess + "%.");
			log.debug("Total profit: " + profit + " with average per trade: " + profit / (winTradeCount + loseTradeCount));
			log.debug("Win trade profits: " + winProfits + " with average per trade: " + winProfits / winTradeCount);
			log.debug("Lose trade profits: " + loseProfits + " with average per trade: " + loseProfits / loseTradeCount);
			
		}
	}
	
	public static void main(String[] args) {
		String equityName = "SPX";
		String sourceFileName = "resources/DoubleMAStrategy/" + equityName +".csv";
		OHLCElementTable elemTable = OHLCUtils.readOHLCDataSourceFile(sourceFileName);
		TwoMACrossingStrategy smacs = new TwoMACrossingStrategy();
		
		int maLength1 = 14;
		int maLength2 = 2;
		double stopWin = Double.NaN;
		double stopLoss = Double.NaN;
		boolean isLong = true;
		
		List<Integer> intParams = new ArrayList<>(2);
		intParams.add(maLength1);
		intParams.add(maLength2);
		
		List<Double> doubleParams = new ArrayList<>(2);
		doubleParams.add(stopWin);
		doubleParams.add(stopLoss);
		
		List<Boolean> boolParams = new ArrayList<>(1);
		boolParams.add(isLong);
		
		smacs.goBackTest(elemTable.getOHCLElementsSortedByDate(), intParams, doubleParams, boolParams, true);
		
		log.debug("Total profit: " + smacs.profit);
		log.debug("Ratio of success: " + smacs.ratioOfSuccess);
		log.debug("Total weighted profit: " + smacs.profit * smacs.ratioOfSuccess / 100.0);

		log.debug("Total percentage profit: " + smacs.getTotalPercentageProfit() + "%");
		log.debug("Winning percetage profit: " + smacs.getWinningPercentageProfit() + "%");
		if(null != smacs.winningTrades && !smacs.winningTrades.isEmpty())
		{
			log.debug("Average winning percetage profit: " + smacs.getWinningPercentageProfit() / smacs.winningTrades.size()+ "%");
		}
		log.debug("Losing percetage profit: " + smacs.getLosingPercentageProfit() + "%");
		if(null != smacs.losingTrades && !smacs.losingTrades.isEmpty())
		{
			log.debug("Average losing percetage profit: " + smacs.getLosingPercentageProfit() / smacs.losingTrades.size()+ "%");
		}
		
		String sortedLosingTradesFileName = "resources/outputs/" + equityName + "_TwoMACrossing_" + (isLong ? "Long_" : "Short_") 
				+ maLength1 + "-" + maLength2 + "_LosingTrades_Sorted.txt";
		smacs.outputSortedLosingTrades(sortedLosingTradesFileName);
		
		String sortedWinningTradesFileName = "resources/outputs/" + equityName + "_TwoMACrossing_" + (isLong ? "Long_" : "Short_") 
					+ maLength1 + "-" + maLength2 + "_WinningTrades_Sorted.txt";
		smacs.outputSortedWinningTrades(sortedWinningTradesFileName);
			
		
		OHLCSequentialTrainingData.generateTextFile("resources/outputs/" + equityName + "_TwoMACrossing_" + (isLong ? "Long" : "Short") 
				+ maLength1 + "-" + maLength2 + "_recording.txt", smacs.sbReport.toString());
		
	}
	
}
