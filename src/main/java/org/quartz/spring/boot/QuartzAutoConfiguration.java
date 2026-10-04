package org.quartz.spring.boot;

import com.mongodb.ConnectionString;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import java.time.Duration;
import java.util.Map;
import java.util.Properties;
import org.quartz.Calendar;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.Trigger;
import org.quartz.impl.StdSchedulerFactory;
import org.quartz.simpl.SimpleThreadPool;
import org.quartz.simpl.VirtualThreadPool;
import org.quartz.spi.SchedulerPlugin;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

/**
 * Auto-configuration for the MongoDB Quartz scheduler.
 *
 * <p>Registers a {@link QuartzSchedulerFactoryBean} when no {@link Scheduler} or factory bean is
 * already defined, and when {@code quartz.scheduler.enabled} is missing or {@code true}. The job
 * store uses the application's {@link MongoClient} when one is present. Otherwise it uses the
 * database from Spring Data's {@code MongoDatabaseFactory}. It does not open a second client from a
 * Quartz-specific URI.
 *
 * <p>{@link JobDetail}, {@link Trigger}, {@link Calendar}, and {@link SchedulerPlugin} beans are
 * registered on that scheduler. Each {@link QuartzSchedulerCustomizer} runs after those properties
 * are applied and can replace them.
 */
@AutoConfiguration(
    afterName = {"org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration"})
@ConditionalOnClass({Scheduler.class, MongoClient.class})
@ConditionalOnProperty(prefix = "quartz.scheduler", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(QuartzProperties.class)
public class QuartzAutoConfiguration {

  /**
   * Builds the scheduler factory from {@link QuartzProperties} and the application's MongoDB
   * client.
   *
   * @param properties scheduler settings under {@code quartz.scheduler}
   * @param mongoClients the application's {@link MongoClient}, when one exists
   * @param customizers callbacks applied after the factory is populated
   * @param jobDetails job definitions to register, in {@link org.springframework.core.Ordered}
   *     order
   * @param calendars calendars to register, keyed by calendar name
   * @param triggers triggers to schedule, in {@link org.springframework.core.Ordered} order
   * @param schedulerPlugins plugins to install, keyed by bean name
   * @param applicationContext context used to autowire jobs and to find a {@code
   *     MongoDatabaseFactory}
   * @param environment source of {@code spring.mongodb.database} and the Mongo URI
   * @return a factory bean that starts the scheduler with the application
   * @throws IllegalStateException if neither a {@link MongoClient} nor a {@code
   *     MongoDatabaseFactory} is available
   */
  @Bean
  @ConditionalOnMissingBean({Scheduler.class, QuartzSchedulerFactoryBean.class})
  QuartzSchedulerFactoryBean quartzScheduler(
      QuartzProperties properties,
      ObjectProvider<MongoClient> mongoClients,
      ObjectProvider<QuartzSchedulerCustomizer> customizers,
      ObjectProvider<JobDetail> jobDetails,
      Map<String, Calendar> calendars,
      ObjectProvider<Trigger> triggers,
      Map<String, SchedulerPlugin> schedulerPlugins,
      ApplicationContext applicationContext,
      Environment environment) {

    MongoClient mongoClient = mongoClients.getIfAvailable();
    MongoDatabase mongoDatabase =
        mongoClient == null ? resolveMongoDatabase(applicationContext) : null;
    if (mongoClient == null && mongoDatabase == null) {
      throw new IllegalStateException(
          "Quartz requires a com.mongodb.client.MongoClient bean or a Spring Data MongoDatabaseFactory");
    }

    QuartzSchedulerFactoryBean factoryBean = new QuartzSchedulerFactoryBean();
    factoryBean.setApplicationContext(applicationContext);
    factoryBean.setAutoStartup(properties.isAutoStartup());
    factoryBean.setStartupDelay(properties.getStartupDelay());
    factoryBean.setWaitForJobsToCompleteOnShutdown(properties.isWaitForJobsToCompleteOnShutdown());
    factoryBean.setOverwriteExistingJobs(properties.isOverwriteExistingJobs());
    factoryBean.setFailFastOnStart(properties.isFailFastOnStart());
    factoryBean.setMongoClient(mongoClient);
    factoryBean.setMongoDatabase(mongoDatabase);
    factoryBean.setQuartzProperties(buildQuartzProperties(properties, environment, mongoDatabase));
    factoryBean.setJobDetails(jobDetails.orderedStream().toArray(JobDetail[]::new));
    factoryBean.setCalendars(calendars);
    factoryBean.setTriggers(triggers.orderedStream().toArray(Trigger[]::new));
    factoryBean.setSchedulerPlugins(schedulerPlugins);
    customizers.orderedStream().forEach(customizer -> customizer.customize(factoryBean));
    return factoryBean;
  }

  /**
   * Maps {@link QuartzProperties} onto Quartz configuration keys, resolving the database name from
   * the environment.
   *
   * @param properties typed scheduler settings
   * @param environment source of the Mongo database name and URI
   * @return properties passed to {@link StdSchedulerFactory#initialize(java.util.Properties)}
   * @see #buildQuartzProperties(QuartzProperties, Environment, MongoDatabase)
   */
  static Properties buildQuartzProperties(QuartzProperties properties, Environment environment) {
    return buildQuartzProperties(properties, environment, null);
  }

  /**
   * Maps {@link QuartzProperties} onto Quartz configuration keys. Entries in {@link
   * QuartzProperties#getProperties()} are applied first. Typed fields, including durations,
   * overwrite the same keys.
   *
   * @param properties typed scheduler settings
   * @param environment used to resolve the database name when {@code mongoDatabase} is absent
   * @param mongoDatabase database already chosen from a {@code MongoDatabaseFactory}, or {@code
   *     null}
   * @return properties passed to {@link StdSchedulerFactory#initialize(java.util.Properties)}
   */
  static Properties buildQuartzProperties(
      QuartzProperties properties, Environment environment, MongoDatabase mongoDatabase) {
    Properties quartz = new Properties();
    properties.getProperties().forEach(quartz::setProperty);
    quartz.setProperty(StdSchedulerFactory.PROP_SCHED_INSTANCE_NAME, properties.getName());
    String threadPoolClass =
        properties.getThreadPool().isVirtual()
            ? VirtualThreadPool.class.getName()
            : SimpleThreadPool.class.getName();
    quartz.setProperty(StdSchedulerFactory.PROP_THREAD_POOL_CLASS, threadPoolClass);
    quartz.setProperty(
        "org.quartz.threadPool.threadCount",
        Integer.toString(properties.getThreadPool().getThreadCount()));
    if (!properties.getThreadPool().isVirtual()) {
      quartz.setProperty(
          "org.quartz.threadPool.threadPriority",
          Integer.toString(properties.getThreadPool().getThreadPriority()));
    }
    quartz.setProperty(
        "org.quartz.jobStore.misfireThreshold",
        Long.toString(toMillis(properties.getMisfireThreshold(), 60_000)));
    quartz.setProperty(
        StdSchedulerFactory.PROP_SCHED_IDLE_WAIT_TIME,
        Long.toString(toMillis(properties.getIdleWaitTime(), 30_000)));
    quartz.setProperty(
        StdSchedulerFactory.PROP_SCHED_BATCH_TIME_WINDOW,
        Long.toString(toMillis(properties.getBatchTimeWindow(), 0)));

    boolean clustered = properties.isClustered();
    String instanceId = properties.getInstanceId();
    if (!StringUtils.hasText(instanceId)) {
      instanceId = StdSchedulerFactory.AUTO_GENERATE_INSTANCE_ID;
    }
    quartz.setProperty(StdSchedulerFactory.PROP_SCHED_INSTANCE_ID, instanceId);
    quartz.setProperty(
        "org.quartz.jobStore.dbName",
        mongoDatabase != null ? mongoDatabase.getName() : resolveDatabase(environment));
    quartz.setProperty("org.quartz.jobStore.collectionPrefix", properties.getCollectionPrefix());
    quartz.setProperty("org.quartz.jobStore.isClustered", Boolean.toString(clustered));
    quartz.setProperty(
        "org.quartz.jobStore.clusterCheckinInterval",
        Long.toString(toMillis(properties.getClusterCheckinInterval(), 15_000)));
    return quartz;
  }

  /**
   * Same database as the Spring Boot Mongo client: {@code spring.mongodb.database} / {@code
   * spring.data.mongodb.database}, or the database in the Mongo URI. Falls back to {@code test},
   * which is Spring Boot's default.
   */
  static String resolveDatabase(Environment environment) {
    if (environment != null) {
      String database =
          firstProperty(environment, "spring.mongodb.database", "spring.data.mongodb.database");
      if (StringUtils.hasText(database)) {
        return database;
      }
      String fromUri =
          databaseFromMongoUri(
              firstProperty(environment, "spring.mongodb.uri", "spring.data.mongodb.uri"));
      if (StringUtils.hasText(fromUri)) {
        return fromUri;
      }
    }
    return "test";
  }

  /**
   * Converts a configured duration to milliseconds.
   *
   * @param duration the configured value, or {@code null} when the property was not set
   * @param fallbackMillis value used when {@code duration} is {@code null}
   * @return {@code duration} in milliseconds, or {@code fallbackMillis}
   */
  static long toMillis(Duration duration, long fallbackMillis) {
    return duration != null ? duration.toMillis() : fallbackMillis;
  }

  /**
   * Returns the database from the first Spring Data {@code MongoDatabaseFactory} bean, or {@code
   * null} when that type is absent or no bean of it exists.
   *
   * @param context the application context, or {@code null}
   * @return the database the factory is bound to, or {@code null}
   * @throws IllegalStateException if a factory bean exists but its database cannot be read
   */
  static MongoDatabase resolveMongoDatabase(ApplicationContext context) {
    if (context == null) {
      return null;
    }
    try {
      Class<?> factoryType = Class.forName("org.springframework.data.mongodb.MongoDatabaseFactory");
      String[] names = context.getBeanNamesForType(factoryType);
      if (names.length == 0) {
        return null;
      }
      Object factory = context.getBean(names[0]);
      return (MongoDatabase) factoryType.getMethod("getMongoDatabase").invoke(factory);
    } catch (ClassNotFoundException e) {
      return null;
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Could not read MongoDatabase from MongoDatabaseFactory", e);
    }
  }

  private static String firstProperty(Environment environment, String... keys) {
    for (String key : keys) {
      String value = environment.getProperty(key);
      if (StringUtils.hasText(value)) {
        return value;
      }
    }
    return null;
  }

  private static String databaseFromMongoUri(String uri) {
    if (!StringUtils.hasText(uri)) {
      return null;
    }
    try {
      return new ConnectionString(uri).getDatabase();
    } catch (RuntimeException e) {
      return null;
    }
  }
}
