package org.quartz.spring.boot;

import org.quartz.InterruptableJob;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.UnableToInterruptJobException;
import org.quartz.simpl.PropertySettingJobFactory;
import org.quartz.spi.TriggerFiredBundle;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;

/**
 * Creates a new job instance for each execution and autowires it. This is the equivalent of
 * Spring's {@code SpringBeanJobFactory} and does not depend on {@code spring-context-support}.
 *
 * <p>The job class is loaded by name from {@link #setClassLoader(ClassLoader)}, then from the
 * thread context loader, then from this class's loader. It is not taken from a {@code Class} object
 * that was serialized onto a scheduler thread. When an {@link AutowireCapableBeanFactory} is set,
 * the instance is created with {@link AutowireCapableBeanFactory#createBean(Class)}, {@link
 * JobDataMap} entries are copied onto matching setters, and the instance is destroyed after the
 * execution shell finishes. Scheduler context entries are copied first, then the job detail map,
 * then the trigger map.
 *
 * <p>Without a bean factory, the job is constructed with its public no-arg constructor and is not
 * destroyed by this factory.
 */
public class AutowireCapableJobFactory extends PropertySettingJobFactory {

  private AutowireCapableBeanFactory beanFactory;
  private ClassLoader classLoader;

  /**
   * Sets the factory that creates and later destroys each job. When {@code null}, jobs are
   * constructed with a public no-arg constructor.
   *
   * @param beanFactory the Spring bean factory, or {@code null}
   */
  public void setBeanFactory(AutowireCapableBeanFactory beanFactory) {
    this.beanFactory = beanFactory;
  }

  /**
   * Sets the loader used to resolve {@link org.quartz.JobDetail#getJobClass()} by name.
   *
   * @param classLoader the application class loader, or {@code null} to fall back to the thread
   *     context loader
   */
  public void setClassLoader(ClassLoader classLoader) {
    this.classLoader = classLoader;
  }

  /**
   * Creates the job for one firing. An {@link InterruptableJob} is returned in a wrapper that still
   * accepts {@link InterruptableJob#interrupt()} and destroys the Spring instance afterward.
   *
   * @param bundle the trigger firing that names the job class and the data maps
   * @param scheduler source of the {@link org.quartz.SchedulerContext} copied onto the job
   * @return a job for this firing
   * @throws SchedulerException if the job class cannot be loaded or instantiated
   */
  @Override
  public Job newJob(TriggerFiredBundle bundle, Scheduler scheduler) throws SchedulerException {
    Class<? extends Job> jobClass = loadJobClass(bundle);
    if (beanFactory == null) {
      try {
        return jobClass.getDeclaredConstructor().newInstance();
      } catch (Exception e) {
        throw new SchedulerException("Problem instantiating class '" + jobClass.getName() + "'", e);
      }
    }
    Job job = beanFactory.createBean(jobClass);
    JobDataMap jobDataMap = new JobDataMap();
    jobDataMap.putAll(scheduler.getContext());
    jobDataMap.putAll(bundle.getJobDetail().getJobDataMap());
    jobDataMap.putAll(bundle.getTrigger().getJobDataMap());
    setBeanProps(job, jobDataMap);
    return wrap(job);
  }

  private Class<? extends Job> loadJobClass(TriggerFiredBundle bundle) throws SchedulerException {
    Class<? extends Job> declared = bundle.getJobDetail().getJobClass();
    if (declared == null) {
      throw new SchedulerException("Job '" + bundle.getJobDetail().getKey() + "' has no job class");
    }
    String name = declared.getName();
    ClassLoader loader = classLoader;
    if (loader == null) {
      loader = Thread.currentThread().getContextClassLoader();
    }
    if (loader == null) {
      loader = getClass().getClassLoader();
    }
    try {
      return Class.forName(name, false, loader).asSubclass(Job.class);
    } catch (ClassNotFoundException | ClassCastException e) {
      throw new SchedulerException("Could not load job class '" + name + "'", e);
    }
  }

  private Job wrap(Job job) {
    if (job instanceof InterruptableJob interruptable) {
      return new DestroyingInterruptableJob(interruptable, beanFactory);
    }
    return new DestroyingJob(job, beanFactory);
  }

  /**
   * Runs the delegate job and destroys the Spring-created instance when the execution shell closes
   * this wrapper.
   */
  static class DestroyingJob implements Job, AutoCloseable {
    final Job job;
    private final AutowireCapableBeanFactory beanFactory;

    DestroyingJob(Job job, AutowireCapableBeanFactory beanFactory) {
      this.job = job;
      this.beanFactory = beanFactory;
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
      job.execute(context);
    }

    @Override
    public void close() {
      beanFactory.destroyBean(job);
    }
  }

  /** {@link DestroyingJob} that also forwards {@link InterruptableJob#interrupt()}. */
  static final class DestroyingInterruptableJob extends DestroyingJob implements InterruptableJob {
    DestroyingInterruptableJob(InterruptableJob job, AutowireCapableBeanFactory beanFactory) {
      super(job, beanFactory);
    }

    @Override
    public void interrupt() throws UnableToInterruptJobException {
      ((InterruptableJob) job).interrupt();
    }
  }
}
