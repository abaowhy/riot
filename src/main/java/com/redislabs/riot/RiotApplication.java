package com.redislabs.riot;

import java.io.IOException;
import java.text.NumberFormat;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.launch.support.SimpleJobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.repository.support.MapJobRepositoryFactoryBean;
import org.springframework.batch.core.step.builder.SimpleStepBuilder;
import org.springframework.batch.core.step.tasklet.TaskletStep;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemStreamReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.support.transaction.ResourcelessTransactionManager;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

import com.redislabs.riot.cli.AbstractCommand;
import com.redislabs.riot.cli.DatabaseReaderCommand;
import com.redislabs.riot.cli.FileReaderCommand;
import com.redislabs.riot.cli.GeneratorReaderCommand;
import com.redislabs.riot.cli.GeneratorReaderHelpCommand;
import com.redislabs.riot.cli.RedisReaderCommand;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "riot", subcommands = { FileReaderCommand.class, DatabaseReaderCommand.class,
		GeneratorReaderCommand.class, GeneratorReaderHelpCommand.class,
		RedisReaderCommand.class }, synopsisSubcommandLabel = "[SOURCE]", commandListHeading = "Sources:%n")
public class RiotApplication extends AbstractCommand implements CommandLineRunner {

	/**
	 * Just to avoid picocli complain in Eclipse console
	 */
	@Option(names = "--spring.output.ansi.enabled", hidden = true)
	private String ansiEnabled;
	@Option(names = "--threads", description = "Number of processing threads", paramLabel = "<count>")
	private int threads = 1;
	@Option(names = "--batch", description = "Number of items in each batch", paramLabel = "<size>")
	private int batchSize = 50;

	private NumberFormat numberFormat = NumberFormat.getIntegerInstance();

	public static void main(String[] args) throws IOException {
		SpringApplication.run(RiotApplication.class, args);
	}

	@Override
	public void run(String... args) throws Exception {
		CommandLine commandLine = new CommandLine(new RiotApplication());
		commandLine.registerConverter(Locale.class, s -> new Locale.Builder().setLanguageTag(s).build());
		commandLine.setCaseInsensitiveEnumValuesAllowed(true);
		commandLine.execute(args);
	}

	public <I, O> void execute(ItemStreamReader<I> reader, ItemProcessor<I, O> processor, ItemWriter<O> writer)
			throws Exception {
		PlatformTransactionManager transactionManager = new ResourcelessTransactionManager();
		MapJobRepositoryFactoryBean jobRepositoryFactory = new MapJobRepositoryFactoryBean(transactionManager);
		jobRepositoryFactory.afterPropertiesSet();
		JobRepository jobRepository = jobRepositoryFactory.getObject();
		JobBuilderFactory jobFactory = new JobBuilderFactory(jobRepository);
		StepBuilderFactory stepFactory = new StepBuilderFactory(jobRepository, transactionManager);
		SimpleStepBuilder<I, O> builder = stepFactory.get("tasklet-step").<I, O>chunk(batchSize);
		builder.reader(reader);
		if (processor != null) {
			builder.processor(processor);
		}
		builder.writer(writer);
		TaskletStep taskletStep = builder.build();
		Step step = taskletStep;
		if (threads > 1) {
			step = stepFactory.get("partitioner-step").partitioner("delegate-step", new IndexedPartitioner(threads))
					.step(taskletStep).taskExecutor(new SimpleAsyncTaskExecutor()).build();
		}
		Job job = jobFactory.get("riot-job").start(step).build();
		SimpleJobLauncher jobLauncher = new SimpleJobLauncher();
		jobLauncher.setJobRepository(jobRepository);
		jobLauncher.setTaskExecutor(new SyncTaskExecutor());
		jobLauncher.afterPropertiesSet();
		JobExecution execution = jobLauncher.run(job, new JobParameters());
		if (execution.getExitStatus().equals(ExitStatus.FAILED)) {
			execution.getAllFailureExceptions().forEach(e -> e.printStackTrace());
		}
		for (StepExecution stepExecution : execution.getStepExecutions()) {
			Duration duration = Duration
					.ofMillis(stepExecution.getEndTime().getTime() - stepExecution.getStartTime().getTime());
			int writeCount = stepExecution.getWriteCount();
			double throughput = (double) writeCount / duration.toMillis() * 1000;
			System.out.println(
					"Wrote " + numberFormat.format(writeCount) + " items in " + duration.get(ChronoUnit.SECONDS)
							+ " seconds (" + numberFormat.format(throughput) + " items/sec)");
		}
	}

}
