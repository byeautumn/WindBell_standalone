package com.byeautumn.wb.ta;

import java.text.SimpleDateFormat;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SecurityTradeUnit implements ITradeUnit {
	private static final Logger log = LoggerFactory.getLogger(SecurityTradeUnit.class);
	private SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy");
	private double tradePrice = Double.NaN;
	private Date tradeTime;
	private double tradeCost = 0.0;
	private boolean isLong = true;
	
	public SecurityTradeUnit(Date tradeTime, double tradePrice)
	{
		this(tradeTime, tradePrice, 0.0);
	}
	
	public SecurityTradeUnit(Date tradeTime, double tradePrice, double tradeCost)
	{
		this(tradeTime, tradePrice, true, tradeCost);
	}
	
	public SecurityTradeUnit(Date tradeTime, double tradePrice, boolean isLong)
	{
		this(tradeTime, tradePrice, isLong, 0.0);
	}

	public SecurityTradeUnit(Date tradeTime, double tradePrice, boolean isLong, double tradeCost)
	{
		if(null == tradeTime || tradePrice == Double.NaN || tradePrice <= 0.0 || tradeCost < 0.0)
		{
			log.error("Invalid input(s).");
		}
		
		this.tradePrice = tradePrice;
		this.tradeTime = tradeTime;
		this.tradeCost = tradeCost;
		this.isLong = isLong;
	}
	
	public double getTradePrice() {
		return tradePrice;
	}

	public Date getTradeTime() {
		return this.tradeTime;
	}

	public double getTradeCost() {
		return this.tradeCost;
	}
	
	public boolean isLong()
	{
		return isLong;
	}
	
	public String printSelf()
	{
		StringBuffer sb = new StringBuffer();
		sb.append(isLong ? "Long at " : "Short at ").append(tradePrice).append(" on ").append(dateFormat.format(this.tradeTime));
		
		return sb.toString();
	}
}
