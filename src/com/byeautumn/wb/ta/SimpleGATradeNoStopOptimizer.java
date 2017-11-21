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


public class SimpleGATradeNoStopOptimizer {
	
	final static class SimpleGAParams {
		final int maLength1;
		final int maLength2;
		
		final static int MA_MAX_LENGTH = 100;
		final static int MA_MIN_LENGTH = 2;
		
		SimpleGAParams(final int maLength1, final int maLength2)
		{
			this.maLength1 = maLength1;
			this.maLength2 = maLength2;
		}
	}
	
	private static final Logger log = LoggerFactory.getLogger(SimpleGATradeNoStopOptimizer.class);
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
		
		return smacs.goBackTestForProfits(elemTable.getOHCLElementsSortedByDate(), Arrays.asList(params.maLength1, params.maLength2), null, Arrays.asList(isLong));
//return smacs.goBackTestForPercentageProfit(elemTable.getOHCLElementsSortedByDate(), Arrays.asList(params.maLength1, params.maLength2), null, Arrays.asList(isLong));

	}
	
	static Codec<SimpleGAParams, IntegerGene> codec(
			final IntRange v1Domain,
			final IntRange v2Domain

		) {
			return Codec.of(
				Genotype.of(
					IntegerChromosome.of(v1Domain),
					IntegerChromosome.of(v2Domain)
				),
				gt -> new SimpleGAParams(
					gt.getChromosome(0).getGene().intValue(),
					gt.getChromosome(1).getGene().intValue()
				)
			);
		}
	
	public static void main(String[] args) {
		final IntRange domain1 = IntRange.of(SimpleGAParams.MA_MIN_LENGTH, SimpleGAParams.MA_MAX_LENGTH);
		final IntRange domain2 = IntRange.of(SimpleGAParams.MA_MIN_LENGTH, SimpleGAParams.MA_MAX_LENGTH);
		
		final Codec<SimpleGAParams, IntegerGene> codec = codec(domain1, domain2);
		
		final Engine<IntegerGene, Double> engine = Engine
				.builder(
					SimpleGATradeNoStopOptimizer::maxProfit,
					codec)
				.maximalPhenotypeAge(110)
				.populationSize(5000)
				.build();
		

		// Create evolution statistics consumer.
		final EvolutionStatistics<Double, ?>
			statistics = EvolutionStatistics.ofNumber();

		final Phenotype<IntegerGene, Double> best =
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
