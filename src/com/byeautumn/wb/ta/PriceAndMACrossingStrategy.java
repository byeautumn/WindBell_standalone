package com.byeautumn.wb.ta;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.byeautumn.wb.data.OHLCElement;
import com.byeautumn.wb.data.OHLCElementTable;
import com.byeautumn.wb.data.OHLCUtils;
import com.byeautumn.wb.dl.DataUtils;
import com.byeautumn.wb.output.OHLCSequentialTrainingData;
import com.tictactec.ta.lib.Core;
import com.tictactec.ta.lib.MInteger;
import com.tictactec.ta.lib.RetCode;

public class PriceAndMACrossingStrategy extends BackTestingStrategry{
	private static final Logger log = LoggerFactory.getLogger(PriceAndMACrossingStrategy.class);
	
	@Override
	protected void goBackTest(List<OHLCElement> elemList, List<Integer> intParams, List<Double> doubleParams, List<Boolean> boolParams, boolean isReplay)
	{
		if(null == elemList || elemList.size() < 1 || null == intParams || intParams.size() < 1)
		{
			log.error("Invalid input(s). Computation failed.");
			return;
		}
		
		int maLengthOpen = intParams.get(0);
		boolean isMALengthCloseDefined = (intParams.size() >= 2);
		int maLengthClose = isMALengthCloseDefined ? intParams.get(1) : 0;
		
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
        
        double[] ma2Out = new double[TOTAL_PERIODS];
        MInteger ma2Begin = new MInteger();
        MInteger ma2Length = new MInteger();
        
		retCode = c.sma(0, closePrice.length - 1, closePrice, maLengthOpen, ma1Begin, ma1Length, ma1Out);
		if(retCode != RetCode.Success)
		{
			log.error("Calculation failed for MA length: " + maLengthOpen);
			return ;
		}
		
		if(isMALengthCloseDefined)
		{
			retCode = c.sma(0, closePrice.length - 1, closePrice, maLengthClose, ma2Begin, ma2Length, ma2Out);
			if(retCode != RetCode.Success)
			{
				log.error("Calculation failed for MA length: " + maLengthClose);
				return ;
			}
		}
		//		int startIdx = Math.max(ma1Begin.value, ma2Begin.value);

		int startIdx = isMALengthCloseDefined ? Math.max(ma1Begin.value, ma2Begin.value) : ma1Begin.value;
		int maxIdx = elemList.size() - 1;
		int winTradeCount = 0;
		double winProfits = 0.0;
		int loseTradeCount = 0;
		double loseProfits = 0.0;
		this.profit = 0.0;
		SecurityPosition sp = null;
		
		if(isReplay && null == this.sbReport)
		{
			this.sbReport = new StringBuffer();
		}
		
		for(int elemIdx = startIdx+1; elemIdx <= maxIdx; ++elemIdx)
		{
			double ma1Curr = ma1Out[elemIdx-ma1Begin.value];
			double ma2Curr = isMALengthCloseDefined ? ma2Out[elemIdx-ma2Begin.value] : ma1Curr;
			OHLCElement currElem = elemList.get(elemIdx);
			OHLCElement prevElem = elemList.get(elemIdx-1);
			double currClosePrice = currElem.getCloseValue();
			double prevClosePrice = prevElem.getCloseValue();
			
			if(isReplay)
				this.sbReport.append(dateFormat.format(currElem.getDateValue())).append(": ").append(currClosePrice).append(", ").append(ma1Curr).append(" | ").append(ma2Curr).append("    ");
			
			if(null == sp || !sp.isOpen())
			{
				double ma1Prev = ma1Out[elemIdx-ma1Begin.value];
				if( ma1Prev < prevClosePrice && ma1Curr >= currClosePrice)
				{					
					if(isReplay)
					{
						log.debug("Open trade at price " + currClosePrice + " on " + dateFormat.format(currElem.getDateValue()) + "; current profit so far is " + profit + ".");
						this.sbReport.append("-->");
					}
					sp = SecurityPosition.open(new SecurityTradeUnit(currElem.getDateValue(), currClosePrice));
					
				}
			}
			else //the position is open
			{
				if(ma2Curr < currClosePrice)
				{					
					sp.close(new SecurityTradeUnit(currElem.getDateValue(), currClosePrice, false));
					double currProfit = sp.getProfit();
					if(currProfit >0.0)
					{
						++winTradeCount;
						winProfits +=currProfit;
						if(null == this.winningTrades)
							this.winningTrades = new ArrayList<>();
						this.winningTrades.add(sp);
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
					
					if(isReplay)
					{
						log.debug("Close trade at price " + currElem.getCloseValue() + " on " + dateFormat.format(currElem.getDateValue()) + " with profit of " + currProfit + "; current profit so far is " + this.profit + ".");
						this.sbReport.append("<--    ").append(sp.getProfit()).append("    ").append(sp.getPercentageProfit()).append("%");
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
		String equityName = "AAPL";
		String sourceFileName = "resources/DoubleMAStrategy/" + equityName +".csv";
		SimpleDateFormat dt = new SimpleDateFormat("MM/dd/yyyy");
		Date trancDate = null;
//		try {
//			trancDate = dt.parse("1/1/2000");
//		} catch (ParseException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
		OHLCElementTable elemTable = OHLCUtils.readOHLCDataSourceFile(sourceFileName);
		PriceAndMACrossingStrategy smacs = new PriceAndMACrossingStrategy();
		
		int maLengthOpen = 3;
		int maLengthClose = 2;
		List<Integer> intParams = new ArrayList<>(2);
		intParams.add(maLengthOpen);
		intParams.add(maLengthClose);
		smacs.goBackTest(elemTable.getOHCLElementsSortedByDateAfter(trancDate), intParams, null, null, true);
		
		
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
		
//		log.debug("************************************* All Losing Trades ********************************* ");
//		if(null != smacs.losingTrades)
//		{
//			for(SecurityPosition sp :smacs.losingTrades)
//			{
//				log.debug(sp.printSelf());
//			}
//		}
		OHLCSequentialTrainingData.generateTextFile("resources/outputs/" + equityName + "_PriceAndMACrossing_recording.txt", smacs.sbReport.toString());
		
	}
	
}
