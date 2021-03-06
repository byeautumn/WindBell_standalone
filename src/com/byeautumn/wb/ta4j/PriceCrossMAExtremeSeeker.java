package com.byeautumn.wb.ta4j;

import static org.jenetics.engine.EvolutionResult.toBestPhenotype;
import static org.jenetics.engine.limit.bySteadyFitness;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jenetics.Genotype;
import org.jenetics.IntegerChromosome;
import org.jenetics.IntegerGene;
import org.jenetics.Phenotype;
import org.jenetics.engine.Codec;
import org.jenetics.engine.Engine;
import org.jenetics.engine.EvolutionStatistics;
import org.jenetics.util.IntRange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ta4j.core.Strategy;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.TimeSeriesManager;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.criteria.TotalProfitCriterion;

import com.byeautumn.wb.data.OHLCElementTable;
import com.byeautumn.wb.data.OHLCUtils;

public class PriceCrossMAExtremeSeeker {
	private static final Logger log = LoggerFactory.getLogger(PriceCrossMAExtremeSeeker.class);
	private static TimeSeries timeSeries;
	private static TimeSeriesManager seriesManager;
	private static TotalProfitCriterion fitnessCriterion;
	private final static int MA_MAX_LENGTH = 100;
	private final static int MA_MIN_LENGTH = 2;
	
	public static void initialize(TimeSeries ts, TotalProfitCriterion fc)
	{
		timeSeries = ts;
		fitnessCriterion = fc;
		seriesManager = new TimeSeriesManager(ts);
	}
	
	private static double fitness(final OptimizationParameters sp)
	{
		if(null == sp || null == sp.getIntParams() || sp.getIntParams().size() != 2)
		{
			log.error("Invalid input(s).");
			return Double.MIN_VALUE;
		}
		
		System.out.println("Processing parameters of " + sp.getIntParams().get(0) + " and " + sp.getIntParams().get(1));
		
		Strategy st = PriceCrossMAStrategy.buildStrategy(timeSeries, sp.getIntParams().get(0), sp.getIntParams().get(1));
		TradingRecord tradingRecord = seriesManager.run(st);
		
		double retValue = fitnessCriterion.calculate(timeSeries, tradingRecord);
		
		System.out.println("Fitness function for" + sp.getIntParams().get(0) + " | " + sp.getIntParams().get(1) + " returns: " + retValue);
		
		return round(retValue, 3);
	}
	
	private static double fitness(List<Integer> maList)
	{
		if(null == maList || maList.size() != 2)
		{
			log.error("Invalid input(s).");
			return Double.MIN_VALUE;
		}
		
		System.out.println("Processing parameters of " + maList.get(0) + " and " + maList.get(1));
		
		Strategy st = PriceCrossMAStrategy.buildStrategy(timeSeries, maList.get(0), maList.get(1));
		TradingRecord tradingRecord = seriesManager.run(st);
		
		double retValue = round(fitnessCriterion.calculate(timeSeries, tradingRecord), 3);
		
		System.out.println("Fitness function for " + maList.get(0) + " | " + maList.get(1) + " returns: " + retValue);
		
		return retValue;
	}
	
	public static double round(double value, int places) {
	    if (places < 0) throw new IllegalArgumentException();

	    long factor = (long) Math.pow(10, places);
	    value = value * factor;
	    long tmp = Math.round(value);
	    return (double) tmp / factor;
	}
	
	static Codec<OptimizationParameters, IntegerGene> codec(
			final IntRange v1Domain

		) {
			return Codec.of(
				Genotype.of(
					IntegerChromosome.of(v1Domain, 2)
				),
				gt -> new OptimizationParameters(Arrays.asList(
					gt.getChromosome(0).getGene(0).intValue(),
					gt.getChromosome(0).getGene(1).intValue()), null, null
				)
			);
		}
	
	public static List<Integer> buildListFromIntRange(int min, int max)
	{
		int size = max - min + 1;
		List<Integer> retList = new ArrayList<>(size);
		for(int idx = 0; idx < size; ++idx)
		{
			retList.add(min + idx);
		}
		
		return retList;
	}
	
	public static void main(String[] args) {
//		final IntRange domain1 = IntRange.of(MA_MIN_LENGTH, MA_MAX_LENGTH);
//		
//		final Codec<OptimizationParameters, IntegerGene> codec = codec(domain1);
		String sourceFileName = "resources/DoubleMAStrategy/NDX.csv";
		OHLCElementTable elemTable = OHLCUtils.readOHLCDataSourceFile(sourceFileName);
		elemTable.setSymbol("NDX");
		
		TimeSeriesAdapter tsAdapter = new TimeSeriesAdapter(elemTable);		
		TimeSeries ts = tsAdapter.getTimeSeries();

		PriceCrossMAExtremeSeeker.initialize(ts, new TotalProfitCriterion());
//		
//		final Engine<IntegerGene, Double> engine = Engine
//				.builder(
//						PriceCrossMAExtremeSeeker::fitness,
//					codec)
//				.maximalPhenotypeAge(70)
//				.populationSize(25)
//				.build();
//		
//
//		// Create evolution statistics consumer.
//		final EvolutionStatistics<Double, ?>
//			statistics = EvolutionStatistics.ofNumber();
//
//		final Phenotype<IntegerGene, Double> best =
//			engine.stream()
//			// Truncate the evolution stream after 7 "steady"
//			// generations.
//			
//			.limit(bySteadyFitness(25))
//			// The evolution will stop after maximal 100
//			// generations.
//			.peek(n -> System.err.println("************ " + n))
//			.limit(1000)
//			// Update the evaluation statistics after
//			// each generation
//			.peek(statistics)
//			
//			// Collect (reduce) the evolution stream to
//			// its best phenotype.
//			.collect(toBestPhenotype());
//
//		System.out.println(statistics);
//		System.out.println(best);

		List<Integer> intList = buildListFromIntRange(2, 100);
		for(int integer : intList)
			System.out.print("" + integer + ", ");
		
		System.out.println();
		System.out.println("Combinations:");
		ListCombinations<Integer> listCombs = new ListCombinations<>(intList, 2);
		List<Integer> nextComb = listCombs.getNextCombination();
		double globalMax = Double.MIN_VALUE;
		List<Integer> gloablMaxComb = null;
		while(null != nextComb)
		{
			for(int integer : nextComb)
				System.out.print("" + integer + ", ");
			System.out.println();
			
			double f = fitness(nextComb);
			if(f > globalMax)
			{
				System.out.println("Current max fitness value: " + f);
				globalMax = f;
				gloablMaxComb = nextComb;
			}
			nextComb = listCombs.getNextCombination();
		}
		
		System.out.println("The extreme is the list combination: [" + gloablMaxComb.get(0) + "][" + gloablMaxComb.get(1) + "] with fitness value of " + globalMax);
	}

}
