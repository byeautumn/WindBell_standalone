package com.byeautumn.wb.ta;

import java.text.SimpleDateFormat;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SecurityPosition {
	private static final Logger log = LoggerFactory.getLogger(SecurityPosition.class);
	private SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy");
	private String positionId;
	private ITradeUnit openTrade;
	private ITradeUnit closeTrade;
	private boolean isOpen = true;
	
	private SecurityPosition(ITradeUnit openTrade) {
		
		this.openTrade = openTrade;
		this.positionId = UUID.randomUUID().toString();
		this.isOpen = true;
	}
	
	public static SecurityPosition open(ITradeUnit openTrade)
	{
		if(null == openTrade)
		{
			log.error("Invalid input.");
			return null;
		}
		
		return new SecurityPosition(openTrade);
	}
	
	public void close(ITradeUnit closeTrade)
	{
		if(null == closeTrade)
		{
			log.error("Invalid input.");
			return;
		}
		
		if(closeTrade.isLong() == openTrade.isLong())
		{
			log.error("Open trade and close trade cannot be long or short at the same time. Position close failed.");
			return;
		}
		
		this.isOpen = false;
		this.closeTrade = closeTrade;
	}

	public double getProfit()
	{
		if(isOpen)
			return 0.0;
		
		if(openTrade.isLong())
			return closeTrade.getTradePrice() - openTrade.getTradePrice() - openTrade.getTradeCost() - closeTrade.getTradeCost();
		else
			return openTrade.getTradePrice() - closeTrade.getTradePrice() - openTrade.getTradeCost() - closeTrade.getTradeCost();
	}
	
	public double getPercentageProfit()
	{
		if(isOpen)
			return 0.0;
		
		if(openTrade.isLong())
			return (closeTrade.getTradePrice() - openTrade.getTradePrice() - openTrade.getTradeCost() - closeTrade.getTradeCost()) / openTrade.getTradePrice() * 100.0;
		else
			return (openTrade.getTradePrice() - closeTrade.getTradePrice() - openTrade.getTradeCost() - closeTrade.getTradeCost()) / openTrade.getTradePrice() * 100.0;
	}
	
	public boolean isOpen()
	{
		return this.isOpen;
	}
	
	public String getPositionId()
	{
		return this.positionId;
	}
	
	public ITradeUnit getOpentTradeUnit()
	{
		return this.openTrade;
	}
	
	public String printSelf()
	{
		StringBuffer sb = new StringBuffer();
		sb.append("Open: ").append(this.openTrade.printSelf()).append(".\n");
		sb.append("Close: ").append(this.closeTrade.printSelf()).append(".\n");
		sb.append("Profit: ").append(this.getProfit()).append("    ").append(this.getPercentageProfit()).append("%.\n");
		
		return sb.toString();
	}
}
