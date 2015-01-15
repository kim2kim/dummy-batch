package com.dummy.batch.config;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URL;
import java.util.jar.JarFile;

import javax.annotation.PostConstruct;

import org.apache.tomcat.jdbc.pool.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.BeanPropertyItemSqlParameterSourceProvider;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.mapping.BeanWrapperFieldSetMapper;
import org.springframework.batch.item.file.mapping.DefaultLineMapper;
import org.springframework.batch.item.file.transform.DelimitedLineTokenizer;
import org.springframework.batch.repeat.RepeatCallback;
import org.springframework.batch.repeat.RepeatContext;
import org.springframework.batch.repeat.RepeatListener;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.batch.repeat.policy.SimpleCompletionPolicy;
import org.springframework.batch.repeat.support.RepeatTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

import com.dummy.batch.domain.Dummy;
import com.dummy.batch.domain.job1.DummyItemProcessor;

/**
 * This is an example of batch configuration using annotation that I have
 * gathered from various online sources.
 * 
 * TODO
 * 
 * Wire in partition and gird size and do multi-threading
 * 
 * @author khimung
 *
 */
@Configuration
@EnableBatchProcessing
@EnableAutoConfiguration
public class BatchConfiguration {
	Logger logger = LoggerFactory.getLogger(BatchConfiguration.class);

	@Autowired
	private JobBuilderFactory jobBuilderFactory;

	@Autowired
	private StepBuilderFactory stepBuilderFactory;

	private RetryTemplate retryTemplate;
	
	private RepeatTemplate template;

	@Value("${batch.maxint}")
	private int maxInt;

	/**
	 * We create the RetryTemplate bean in the batch context, which allows us to
	 * reuse if needed. However, we could be create it within this class if we
	 * need a specialized RetryTemplate.
	 */
	@PostConstruct
	public void init(){
		this.template = new RepeatTemplate();
		template.setCompletionPolicy(new SimpleCompletionPolicy(2));
		
		this.retryTemplate = new RetryTemplate();
		SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
		retryPolicy.setMaxAttempts(this.maxInt);
		
		/*
		retryTemplate.registerListener(new RetryListener(){

			@Override
			public <T, E extends Throwable> boolean open(RetryContext context,
					RetryCallback<T, E> callback) {
				logger.info("retry open");
				return false;
			}

			@Override
			public <T, E extends Throwable> void close(RetryContext context,
					RetryCallback<T, E> callback, Throwable throwable) {
				logger.info("retry close");
			}

			@Override
			public <T, E extends Throwable> void onError(RetryContext context,
					RetryCallback<T, E> callback, Throwable throwable) {
				logger.info("retry onError");
			}
			
		});
		*/
		
		System.out.println("********** " + this.maxInt);
		this.retryTemplate.setRetryPolicy(retryPolicy);
	}

	// tag::jobstep[]
	@Bean
	public Job dumbJob(JobBuilderFactory jobs, Step step1, Step step2) {
		System.out.println("dumbJob");
		return jobs.get("dumbJob").incrementer(new RunIdIncrementer())
				.flow(step1).next(step2).end().build();
	}

	@Bean
	public ItemReader<Dummy> reader() {
		FlatFileItemReader<Dummy> reader = new FlatFileItemReader<Dummy>();
		reader.setResource(new ClassPathResource("sample-data.csv"));
		reader.setLineMapper(new DefaultLineMapper<Dummy>() {
			{
				setLineTokenizer(new DelimitedLineTokenizer() {
					{
						setNames(new String[] { "level" });
					}
				});
				setFieldSetMapper(new BeanWrapperFieldSetMapper<Dummy>() {
					{
						setTargetType(Dummy.class);
					}
				});
			}
		});
		return reader;
	}

	@Bean
	public ItemProcessor<Dummy, Dummy> processor() {
		System.out.println("dummy processor");
		return new DummyItemProcessor();
	}

	@Bean
	public ItemWriter<Dummy> writer(DataSource dataSource) {
		JdbcBatchItemWriter<Dummy> writer = new JdbcBatchItemWriter<Dummy>();
		writer.setItemSqlParameterSourceProvider(new BeanPropertyItemSqlParameterSourceProvider<Dummy>());
		writer.setSql("INSERT INTO dummy (level) VALUES (:level)");
		writer.setDataSource(dataSource);
		System.out.println("writer complete");
		return writer;
	}

	@Bean
	public Step step2(StepBuilderFactory stepBuilderFactory,
			ItemReader<Dummy> reader, ItemWriter<Dummy> writer,
			ItemProcessor<Dummy, Dummy> processor) {
		System.out.println("step2");
		return stepBuilderFactory.get("step2").<Dummy, Dummy> chunk(2)
				.reader(reader).processor(processor).writer(writer).build();
	}

	@Bean
	public Step step1() {
		return stepBuilderFactory.get("step1").tasklet(new Tasklet() {
			public RepeatStatus execute(StepContribution contribution,
					ChunkContext chunkContext) {
				
			return retryTemplate.execute(new RetryCallback<RepeatStatus, RuntimeException>() {
				
						public RepeatStatus doWithRetry(RetryContext context)
								throws RuntimeException {
							
							logger.debug("Attempt number " + context.getRetryCount());

							if(context.getRetryCount() == 3){
								logger.debug("Found third retry.  Creating the test data file so it can be retrieved");
								createMyTestDataFile();
							}
							
							return template.iterate(new RepeatCallback() {	
								
							    public RepeatStatus doInIteration(RepeatContext context) {
							    	logger.debug("Iterating again");
									ClassLoader loader = this.getClass().getClassLoader();
									URL url = loader.getResource("com/dummy/batch/DummyJob.class");
									
									try {
										JarURLConnection connection = (JarURLConnection) url.openConnection();
										JarFile jarFile = connection.getJarFile();
										File jarPath = new File(jarFile.getName());

										String path = jarPath.getParent().concat(File.separator +"sample-data.csv");
										logger.info(path);
										File file = new File(path);
										if (!file.canRead()) {
											logger.info("step1 cannot read file");
											// throw an exception
											throw new RuntimeException();
										}
									} catch (IOException e) {
										// TODO Auto-generated catch block
										e.printStackTrace();
									}

									logger.debug("sending finish in iterate");
							        return RepeatStatus.FINISHED;
							    }
							});
						}
					});
			}
		}).listener(new RepeatListener() {

			@Override
			public void after(RepeatContext arg0, RepeatStatus arg1) {
				logger.info(arg0.getStartedCount() + " after");

			}

			@Override
			public void before(RepeatContext arg0) {
				logger.info(arg0.getStartedCount() + " before");

			}

			@Override
			public void close(RepeatContext arg0) {
				logger.info(arg0.getStartedCount() + " close");

			}

			@Override
			public void onError(RepeatContext arg0, Throwable arg1) {
				logger.info(arg0.getStartedCount() + " error");

			}

			@Override
			public void open(RepeatContext arg0) {
				logger.info(arg0.getStartedCount() + " open");

			}

		}).build();
	}
	
	private void createMyTestDataFile(){
		String path = System.getProperty("user.home") + File.separator + "Documents/dummy-batch/target/sample-data.csv";
		logger.info(path);
		File file = new File(path);
		
		try (FileOutputStream out = new FileOutputStream(file)){
			if (!file.exists()) {
				try {
					file.createNewFile();
				} catch (IOException e) {
					logger.error("Cant create file");
				}
			}
			out.write("1".getBytes());
			out.write("5".getBytes());
			out.write("1".getBytes());
			out.write("3".getBytes());
			out.write("2".getBytes());
			out.write("3".getBytes());
			out.write("1".getBytes());
			out.write("2".getBytes());
			out.write("6".getBytes());
		} catch (FileNotFoundException e1) {
			logger.error("File not found exception");
		} catch (IOException e1) {
			logger.error("Unable to write to file sample-data.csv : " + path);
		}
	}
}