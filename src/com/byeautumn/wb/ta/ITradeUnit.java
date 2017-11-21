package com.byeautumn.wb.ta;

import java.util.Date;

public interface ITradeUnit {
	public double getTradePrice();
	public Date getTradeTime();
	public double getTradeCost();
	public boolean isLong();
	public String printSelf();
}
