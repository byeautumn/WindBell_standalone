package com.byeautumn.wb.ta4j;

import java.util.List;

public class OptimizationParameters {
	private List<Integer> intParams;
	private List<Double> doubleParams;
	private List<Boolean> boolParams;
	
	OptimizationParameters(List<Integer> intParams, List<Double> doubleParams, List<Boolean> boolParams)
	{
		this.boolParams = boolParams;
		this.intParams = intParams;
		this.doubleParams = doubleParams;
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


	public static void main(String[] args) {
		// TODO Auto-generated method stub

	}

}
