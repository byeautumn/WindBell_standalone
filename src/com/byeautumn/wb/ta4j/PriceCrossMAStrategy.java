package com.byeautumn.wb.ta4j;

import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Decimal;
import org.ta4j.core.Rule;
import org.ta4j.core.Strategy;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.TimeSeriesManager;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.criteria.TotalProfitCriterion;
import org.ta4j.core.indicators.DoubleEMAIndicator;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.StochasticOscillatorKIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.trading.rules.CrossedDownIndicatorRule;
import org.ta4j.core.trading.rules.CrossedUpIndicatorRule;
import org.ta4j.core.trading.rules.OverIndicatorRule;
import org.ta4j.core.trading.rules.UnderIndicatorRule;

import com.byeautumn.wb.data.OHLCElementTable;
import com.byeautumn.wb.data.OHLCUtils;

public class PriceCrossMAStrategy extends BackTestingStrategy{

    /**
     * @param series a time series
     * @return a strategy of seeking price crossing a certain moving average for buying or selling points.
     */
    public static Strategy buildStrategy(TimeSeries series, int entryTimeFrame, int exitTimeFrame) {
        if (series == null) {
            throw new IllegalArgumentException("Series cannot be null");
        }
System.out.println("Building strategy with entryTimeFrame:" + entryTimeFrame + " and exitTimeFrame: " + exitTimeFrame);
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        
        DoubleEMAIndicator demaEntry = new DoubleEMAIndicator(closePrice, entryTimeFrame);
        DoubleEMAIndicator demaExit = new DoubleEMAIndicator(closePrice, exitTimeFrame);
        
      
        // Entry rule
        Rule entryRule = new OverIndicatorRule(closePrice, demaEntry);
        
        // Exit rule
        Rule exitRule = new UnderIndicatorRule(closePrice, demaExit);
        
        return new BaseStrategy(entryRule, exitRule);
    }
    
    
	public static void main(String[] args) {
		String sourceFileName = "resources/DoubleMAStrategy/NDX.csv";
		OHLCElementTable elemTable = OHLCUtils.readOHLCDataSourceFile(sourceFileName);
		elemTable.setSymbol("NDX");
		
		TimeSeriesAdapter tsAdapter = new TimeSeriesAdapter(elemTable);		
		TimeSeries ts = tsAdapter.getTimeSeries();
		
		int entryTimeFrame = 11;
		int exitTimeFrame = 6;
		
		Strategy st = PriceCrossMAStrategy.buildStrategy(ts, entryTimeFrame, exitTimeFrame);
		
        // Running the strategy
        TimeSeriesManager seriesManager = new TimeSeriesManager(ts);
        TradingRecord tradingRecord = seriesManager.run(st);
        System.out.println("Number of trades for the strategy: " + tradingRecord.getTradeCount());

        // Analysis
        System.out.println("Total profit for the strategy: " + new TotalProfitCriterion().calculate(ts, tradingRecord));
        
        Trade lastTrade = tradingRecord.getLastTrade();
        System.out.println("Last trade: " + lastTrade.toString());

	}

}
