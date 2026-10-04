package org.quartz.spring.boot;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.quartz.Calendar;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.impl.StdSchedulerFactory;
import org.quartz.spi.SchedulerPlugin;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.SmartLifecycle;

/**
 * Lifecycle wrapper around {@link StdSchedulerFactory}. It fills the role of Spring's {@code
 * SchedulerFactoryBean} for this MongoDB scheduler: there is no JDBC {@code DataSource}.
 *
 * <p>{@link #afterPropertiesSet()} creates the {@link Scheduler}, installs {@link
 * AutowireCapableJobFactory}, and registers calendars, jobs, and triggers. It does not start the
 * scheduler. {@link #start()} does that, after {@link #setStartupDelay(Duration)} when the delay is
 * positive. The factory is a {@link SmartLifecycle} with phase {@link Integer#MAX_VALUE}, so it
 * starts after other beans and stops before them.
 *
 * <p>Pass either {@link #setMongoClient(MongoClient)} or {@link #setMongoDatabase(MongoDatabase)}.
 * The store does not close a client it does not create. {@code setQuartzProperties} supplies the
 * {@code org.quartz.*} keys, including the job-store class and database name. Generated setters
 * also accept {@code autoStartup}, {@code waitForJobsToCompleteOnShutdown}, {@code
 * overwriteExistingJobs}, and {@code failFastOnStart}.
 */
@Slf4j
public class QuartzSchedulerFactoryBean
    implements FactoryBean<Scheduler>,
        InitializingBean,
        DisposableBean,
        SmartLifecycle,
        ApplicationContextAware {

  @Setter private Properties quartzProperties = new Properties();
  private JobDetail[] jobDetails = new JobDetail[0];
  private Trigger[] triggers = new Trigger[0];
  private Map<String, Calendar> calendars = Collections.emptyMap();
  private Map<String, SchedulerPlugin> schedulerPlugins = Collections.emptyMap();
  @Setter private boolean autoStartup = true;
  private Duration startupDelay = Duration.ZERO;
  @Setter private boolean waitForJobsToCompleteOnShutdown = true;
  @Setter private boolean overwriteExistingJobs;
  @Setter private boolean failFastOnStart = true;
  @Setter private MongoClient mongoClient;
  @Setter private MongoDatabase mongoDatabase;
  private ApplicationContext applicationContext;
  private Scheduler scheduler;
  private final AtomicBoolean running = new AtomicBoolean(false);
  private final AtomicBoolean destroyed = new AtomicBoolean(false);

  /**
   * Sets the jobs registered during {@link #afterPropertiesSet()}. A {@code null} array is stored
   * as an empty array. Each job is added with {@code storeNonDurableWhileAwaitingScheduling} so a
   * job that is not durable can be registered before its trigger.
   *
   * @param jobDetails jobs to add, or {@code null}
   */
  public void setJobDetails(JobDetail[] jobDetails) {
    this.jobDetails = jobDetails != null ? jobDetails : new JobDetail[0];
  }

  /**
   * Sets the triggers scheduled during {@link #afterPropertiesSet()}. A trigger whose key is
   * already stored is rescheduled only when {@code overwriteExistingJobs} is {@code true}.
   * Otherwise it is left unchanged.
   *
   * @param triggers triggers to schedule, or {@code null}
   */
  public void setTriggers(Trigger[] triggers) {
    this.triggers = triggers != null ? triggers : new Trigger[0];
  }

  /**
   * Sets the calendars added during {@link #afterPropertiesSet()}. Each calendar replaces an
   * existing calendar of the same name and updates triggers that use it.
   *
   * @param calendars calendars keyed by name, or {@code null}
   */
  public void setCalendars(Map<String, Calendar> calendars) {
    this.calendars = calendars != null ? calendars : Collections.emptyMap();
  }

  /**
   * Sets the plugins installed when the scheduler is created. Keys are the plugin names passed to
   * {@link org.quartz.spi.SchedulerPlugin#initialize(String, Scheduler)}.
   *
   * @param schedulerPlugins plugins keyed by name, or {@code null}
   */
  public void setSchedulerPlugins(Map<String, SchedulerPlugin> schedulerPlugins) {
    this.schedulerPlugins = schedulerPlugins != null ? schedulerPlugins : Collections.emptyMap();
  }

  /**
   * Sets how long {@link #start()} waits before calling {@link Scheduler#start()}. A {@code null}
   * or non-positive delay starts the scheduler immediately. A positive delay uses {@link
   * Scheduler#startDelayed(Duration)} and does not block this method.
   *
   * @param startupDelay delay before start, or {@code null}
   */
  public void setStartupDelay(Duration startupDelay) {
    this.startupDelay = startupDelay != null ? startupDelay : Duration.ZERO;
  }

  /**
   * Stores the context used to load job classes and to autowire each job execution.
   *
   * @param applicationContext the running application context
   */
  @Override
  public void setApplicationContext(@NonNull ApplicationContext applicationContext) {
    this.applicationContext = applicationContext;
  }

  /**
   * Creates the scheduler, installs {@link AutowireCapableJobFactory} when an application context
   * is present, and registers calendars, jobs, and triggers. The scheduler stays in standby until
   * {@link #start()}.
   *
   * @throws Exception if scheduler initialization or registration fails
   */
  @Override
  public void afterPropertiesSet() throws Exception {
    StdSchedulerFactory factory = new StdSchedulerFactory();
    factory.setMongoClient(mongoClient);
    factory.setMongoDatabase(mongoDatabase);
    if (applicationContext != null) {
      factory.setJobClassLoader(applicationContext.getClassLoader());
    }
    factory.setSchedulerPlugins(schedulerPlugins);
    factory.initialize(quartzProperties);
    this.scheduler = factory.getScheduler();
    if (applicationContext != null) {
      AutowireCapableJobFactory jobFactory = new AutowireCapableJobFactory();
      AutowireCapableBeanFactory beanFactory = applicationContext.getAutowireCapableBeanFactory();
      jobFactory.setBeanFactory(beanFactory);
      jobFactory.setClassLoader(applicationContext.getClassLoader());
      scheduler.setJobFactory(jobFactory);
    }
    registerCalendars();
    registerJobsAndTriggers();
  }

  private void registerCalendars() throws SchedulerException {
    for (Map.Entry<String, Calendar> entry : calendars.entrySet()) {
      scheduler.addCalendar(entry.getKey(), entry.getValue(), true, true);
    }
  }

  private void registerJobsAndTriggers() throws SchedulerException {
    for (JobDetail jobDetail : jobDetails) {
      scheduler.addJob(jobDetail, overwriteExistingJobs, true);
    }
    for (Trigger trigger : triggers) {
      if (scheduler.checkExists(trigger.getKey())) {
        if (overwriteExistingJobs) {
          scheduler.rescheduleJob(trigger.getKey(), trigger);
        }
      } else {
        scheduler.scheduleJob(trigger);
      }
    }
  }

  /**
   * Returns the scheduler created by {@link #afterPropertiesSet()}.
   *
   * @return the scheduler, or {@code null} before initialization
   */
  @Override
  public Scheduler getObject() {
    return scheduler;
  }

  /**
   * Returns {@link Scheduler}, including before {@link #afterPropertiesSet()} has run.
   *
   * @return {@code Scheduler.class}
   */
  @Override
  public Class<?> getObjectType() {
    return Scheduler.class;
  }

  /**
   * The factory always produces the one scheduler it created.
   *
   * @return {@code true}
   */
  @Override
  public boolean isSingleton() {
    return true;
  }

  /**
   * Starts the scheduler, or schedules a delayed start. When {@code failFastOnStart} is {@code
   * true}, a {@link SchedulerException} fails context refresh. Otherwise the error is logged and
   * the scheduler thread retries.
   */
  @Override
  public void start() {
    if (scheduler == null) {
      return;
    }
    try {
      if (!startupDelay.isZero() && !startupDelay.isNegative()) {
        scheduler.startDelayed(startupDelay);
      } else {
        scheduler.start();
      }
      running.set(scheduler.isStarted());
    } catch (SchedulerException ex) {
      if (failFastOnStart) {
        throw new IllegalStateException("Could not start Quartz scheduler", ex);
      }
      log.error("Could not start Quartz scheduler; the scheduler thread will retry", ex);
    }
  }

  /** Stops the lifecycle by shutting the scheduler down. Equivalent to {@link #destroy()}. */
  @Override
  public void stop() {
    destroy();
  }

  /**
   * Reports whether the scheduler has been started and has not been destroyed.
   *
   * @return {@code true} when the scheduler is started
   */
  @Override
  public boolean isRunning() {
    try {
      return scheduler != null && !destroyed.get() && (running.get() || scheduler.isStarted());
    } catch (SchedulerException ex) {
      return running.get();
    }
  }

  /**
   * Whether Spring should call {@link #start()} after the context is refreshed.
   *
   * @return the configured auto-startup flag
   */
  @Override
  public boolean isAutoStartup() {
    return autoStartup;
  }

  /**
   * Starts this bean after every other lifecycle bean and stops it before them.
   *
   * @return {@link Integer#MAX_VALUE}
   */
  @Override
  public int getPhase() {
    return Integer.MAX_VALUE;
  }

  /**
   * Shuts the scheduler down once. When {@code waitForJobsToCompleteOnShutdown} is {@code true},
   * in-flight jobs are allowed to finish.
   *
   * @throws IllegalStateException if shutdown fails
   */
  @Override
  public void destroy() {
    if (!destroyed.compareAndSet(false, true)) {
      return;
    }
    running.set(false);
    try {
      if (scheduler != null && !scheduler.isShutdown()) {
        scheduler.shutdown(waitForJobsToCompleteOnShutdown);
      }
    } catch (SchedulerException ex) {
      throw new IllegalStateException("Could not shut down Quartz scheduler", ex);
    }
  }
}
