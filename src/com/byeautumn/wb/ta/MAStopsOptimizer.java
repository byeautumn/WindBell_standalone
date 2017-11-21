package com.byeautumn.wb.ta;

import java.util.Arrays;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.byeautumn.wb.data.OHLCElementTable;
import com.byeautumn.wb.data.OHLCUtils;

import static org.jenetics.engine.EvolutionResult.toBestPhenotype;
import static org.jenetics.engine.limit.bySteadyFitness;

import org.jenetics.DoubleChromosome;
import org.jenetics.DoubleGene;
import org.jenetics.Genotype;
import org.jenetics.IntegerChromosome;
import org.jenetics.IntegerGene;
import org.jenetics.Phenotype;
import org.jenetics.engine.Codec;
import org.jenetics.engine.Engine;
import org.jenetics.engine.EvolutionStatistics;
import org.jenetics.util.DoubleRange;
import org.jenetics.util.IntRange;


public class MAStopsOptimizer {
	
	final static class SimpleGAParams {
		final double stopWin;
		final double stopLoss;
		
		final static double STOP_PERCENTAGE_MIN = 0.0;
		final static double STOP_PERCENTAGE_MAX = 0.5;
		
		SimpleGAParams(final double stopWin, final double stopLoss)
		{
			this.stopWin = stopWin;
			this.stopLoss = stopLoss;
		}
	}
	
	private static final Logger log = LoggerFactory.getLogger(MAStopsOptimizer.class);
	private static String sourceFileName = "resources/DoubleMAStrategy/SPX.csv";
	private static OHLCElementTable elemTable = OHLCUtils.readOHLCDataSourceFile(sourceFileName);
	
	private static double maxProfit(final SimpleGAParams params)
	{
		if(null == params)
		{
			log.error("Invalid input(s).");
			return Double.MIN_VALUE;
		}
		
		TwoMACrossingStrategy smacs = new TwoMACrossingStrategy();
		boolean isLong = true;
		return smacs.goBackTestForWeightedProfit(elemTable.getOHCLElementsSortedByDate(), Arrays.asList(6, 11), Arrays.asList(params.stopWin, params.stopLoss), Arrays.asList(isLong), false);
	}
	
	static Codec<SimpleGAParams, DoubleGene> codec(
			final DoubleRange v3Domain,
			final DoubleRange v4Domain

		) {
			return Codec.of(
				Genotype.of(
					DoubleChromosome.of(v3Domain),
					DoubleChromosome.of(v4Domain)
				),
				gt -> new SimpleGAParams(
					gt.getChromosome(0).getGene().doubleValue(),
					gt.getChromosome(1).getGene().doubleValue()
				)
			);
		}
	
	public static void main(String[] args) {
		final DoubleRange domain3 = DoubleRange.of(SimpleGAParams.STOP_PERCENTAGE_MIN, SimpleGAParams.STOP_PERCENTAGE_MAX);
		final DoubleRange domain4 = DoubleRange.of(SimpleGAParams.STOP_PERCENTAGE_MIN, SimpleGAParams.STOP_PERCENTAGE_MAX);
		
		final Codec<SimpleGAParams, DoubleGene> codec = codec(domain3, domain4);
		
		final Engine<DoubleGene, Double> engine = Engine
				.builder(
					MAStopsOptimizer::maxProfit,
					codec)
				.maximalPhenotypeAge(110)
				.populationSize(5000)
				.build();
		

			// Create evolution statistics consumer.
			final EvolutionStatistics<Double, ?>
				statistics = EvolutionStatistics.ofNumber();

			final Phenotype<DoubleGene, Double> best =
				engine.stream()
				// Truncate the evolution stream after 7 "steady"
				// generations.
				.limit(bySteadyFitness(150))
				// The evolution will stop after maximal 100
				// generations.
				.limit(25000)
				// Update the evaluation statistics after
				// each generation
				.peek(statistics)
				// Collect (reduce) the evolution stream to
				// its best phenotype.
				.collect(toBestPhenotype());

			System.out.println(statistics);
			System.out.println(best);
	}

}
