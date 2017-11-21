package com.byeautumn.wb.ta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import org.jenetics.DoubleChromosome;
import org.jenetics.DoubleGene;
import org.jenetics.Genotype;
import org.jenetics.IntegerChromosome;
import org.jenetics.IntegerGene;
import org.jenetics.engine.Codec;
import org.jenetics.util.DoubleRange;
import org.jenetics.util.IntRange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JAOptimizer {
	private static final Logger log = LoggerFactory.getLogger(JAOptimizer.class);
//	static Codec<SimpleGAParams, DoubleGene> doubleCodec(final List<DoubleRange> doubleDomainList) {
//		final List<DoubleChromosome> chromosomes = new ArrayList<>();
		
//		SimpleGAParams sgap = new SimpleGAParams(null, null, null);
//			return Codec.of(
//				Genotype.of(
////					doubleDomainList.stream().map(DoubleChromosome::of).toList<DoubleChromosome>()
//						chromosomes
//				),
//				sgap
//			);
//		}
	
//	static Codec<SimpleGAParams, IntegerGene> intCodec(final List<IntRange> intDomainList) 
//	{
//		if(null == intDomainList)
//		{
//			log.error("Invalid input.");
//			return null;
//		}
//		
//		List<IntegerChromosome> chromosomes = new ArrayList<>(intDomainList.size());
//	
//		for(IntRange domain : intDomainList)
//		{
//			chromosomes.add(IntegerChromosome.of(domain));
//		}
//		
//		return Codec.of(
//			JAOptimizer::encodeToIntGenotype,
//			JAOptimizer::decodeFromIntGenotype
//		);
//	}
//	
//	static Function<Genotype<IntegerGene>, SimpleGAParams>  decodeFromIntGenotype(Genotype<IntegerGene> gt)
//	{
//		return null;
//	}
	
	static Function<Genotype<DoubleGene>, SimpleGAParams>  decodeFromIntGenotype()
	{
		return null;
	}
//	
//	static Genotype<IntegerGene> encodeToIntGenotype(List<IntRange> intDomainList)
//	{
//		
//	}
}
