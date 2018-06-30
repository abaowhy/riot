package com.redislabs.recharge.generator;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import com.redislabs.recharge.batch.KeyConfiguration;
import com.redislabs.recharge.batch.MapItemProcessor;
import com.redislabs.recharge.batch.StepProvider;

@Configuration
public class LoadGeneratorStep implements StepProvider {

	@Autowired
	private FakeItemReader reader;

	@Autowired
	private KeyConfiguration keyConfig;

	@Autowired
	private GeneratorConfiguration config;

	public FakeItemReader getReader() {
		return reader;
	}

	public MapItemProcessor getProcessor() {
		return new MapItemProcessor(keyConfig.getPrefix(), getKeyFields(), keyConfig.getSeparator());
	}

	private String[] getKeyFields() {
		if (keyConfig.getFields() == null || keyConfig.getFields().length == 0) {
			return new String[] { config.getFields().keySet().iterator().next() };
		}
		return keyConfig.getFields();
	}

}