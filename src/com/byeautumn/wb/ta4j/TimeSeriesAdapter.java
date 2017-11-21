package com.byeautumn.wb.ta4j;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ta4j.core.BaseTick;
import org.ta4j.core.BaseTimeSeries;
import org.ta4j.core.Tick;
import org.ta4j.core.TimeSeries;

import com.byeautumn.wb.data.OHLCElement;
import com.byeautumn.wb.data.OHLCElementTable;
import com.byeautumn.wb.data.OHLCUtils;

public class TimeSeriesAdapter {
	private static final Logger log = LoggerFactory.getLogger(TimeSeriesAdapter.class);
	private static final String DEFAULT_TIMESERIES_NAME = "Your Security";
	private TimeSeries timeSeries;

	public TimeSeriesAdapter(OHLCElementTable ohlcTable) {
		if (null == ohlcTable) {
			log.error("Invaid input.");
			return;
		}

		String timeSeriesName = ohlcTable.getSymbol();
		if (null == timeSeriesName || timeSeriesName.isEmpty())
			timeSeriesName = DEFAULT_TIMESERIES_NAME;

		this.timeSeries = buildTimeSeries(timeSeriesName, ohlcTable);
	}

	private TimeSeries buildTimeSeries(String timeSeriesName, OHLCElementTable ohlcTable) {
		List<Tick> ticks = new ArrayList<>(ohlcTable.size());
		for (OHLCElement elem : ohlcTable.getOHCLElementsSortedByDate()) {
			ZonedDateTime date = toZonedDateTime(elem.getDateValue());
			double open = elem.getOpenValue();
			double high = elem.getHighValue();
			double low = elem.getLowValue();
			double close = elem.getCloseValue();
			double volume = elem.getVolumeValue();

			ticks.add(new BaseTick(date, open, high, low, close, volume));
		}

		return new BaseTimeSeries(timeSeriesName, ticks);
	}

	public TimeSeriesAdapter(TimeSeries timeSeries) {
		this.timeSeries = timeSeries;
	}

	public TimeSeries getTimeSeries() {
		return this.timeSeries;
	}

	public static ZonedDateTime toZonedDateTime(Date utilDate) {
		if (utilDate == null) {
			return null;
		}
		final ZoneId systemDefault = ZoneId.systemDefault();
		return ZonedDateTime.ofInstant(utilDate.toInstant(), systemDefault);
	}

	public static void main(String[] args) {
		String sourceFileName = "resources/DoubleMAStrategy/NDX.csv";
		OHLCElementTable elemTable = OHLCUtils.readOHLCDataSourceFile(sourceFileName);
		elemTable.setSymbol("NDX");
		
		System.out.println("OHLCElementTable size: " + elemTable.size());
		System.out.println("First ohlcElement: " + elemTable.getEarliest().printSelf());
		
		TimeSeriesAdapter tsAdapter = new TimeSeriesAdapter(elemTable);		
		TimeSeries ts = tsAdapter.getTimeSeries();
		System.out.println("Converted time series size: " + ts.getTickCount());
		System.out.println("First tick: " + ts.getFirstTick().toString());
	}

}
