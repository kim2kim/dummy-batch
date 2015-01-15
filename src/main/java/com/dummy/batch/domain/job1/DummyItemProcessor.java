package com.dummy.batch.domain.job1;

import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemProcessor;

import com.dummy.batch.domain.Dummy;

/**
 * 
 * @author khimung
 *
 */
public class DummyItemProcessor implements ItemProcessor<Dummy, Dummy> {
	
	Logger logger = LoggerFactory.getLogger(DummyItemProcessor.class);

	@Override
	public Dummy process(Dummy arg0) throws Exception {
		Random random = new Random(3);
		Dummy transformed = new Dummy();
		int value = random.nextInt(3) + arg0.getLevel();
		logger.debug("value is " + value);
		transformed.setLevel(value);
		return transformed;
	}

}
