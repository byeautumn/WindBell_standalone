package com.byeautumn.wb.ta4j;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

import org.apache.commons.math3.util.Combinations;

public class ListCombinations<T> {

	private List<T> list;
	private Combinations combinations;
	private Iterator<int[]> currIter;
	public ListCombinations (List<T> list, int k)
	{
		this.list = list;
		
		if(k > this.list.size())
		{
			
			k = list.size();
		}
		if(k < 0)
			k = 0;
		
		this.combinations = new Combinations(list.size(), k);
	}


	public List<T> getNextCombination()
	{
		if(null == this.combinations)
			return null;
		
		if(null == this.currIter)
			this.currIter = this.combinations.iterator();
		
		if(!this.currIter.hasNext())
			return null;
		
		int[] idxArr = this.currIter.next();
		int k = this.combinations.getK();
		List<T> retList = new ArrayList<T>(k);
		for(int idx : idxArr)
			retList.add(this.list.get(idx));
		
		return retList;
	}
	
	public static void main(String[] args) {
		List<String> strList = Arrays.asList("one", "two", "three", "four");

		ListCombinations<String> listCombs = new ListCombinations<>(strList, 2);
		List<String> nextComb = listCombs.getNextCombination();
		while(null != nextComb)
		{
			for(String s : nextComb)
				System.out.print(s + ", ");
			System.out.println();
			nextComb = listCombs.getNextCombination();
		}
		
	}
}
