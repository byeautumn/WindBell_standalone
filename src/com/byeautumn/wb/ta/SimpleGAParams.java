package com.byeautumn.wb.ta;

import java.util.List;

public class SimpleGAParams {
	private List<Integer> intParams;
	private List<Double> doubleParams;
	private List<Boolean> boolParams;
	
	public SimpleGAParams(List<Integer> intParams, List<Double> doubleParams, List<Boolean> boolParams)
	{
		this.intParams = intParams;
		this.doubleParams = doubleParams;
		this.boolParams = boolParams;
	}

	public List<Integer> getIntParams() {
		return intParams;
	}

	public List<Double> getDoubleParams() {
		return doubleParams;
	}

	public List<Boolean> getBoolParams() {
		return boolParams;
	}
	
	
}
